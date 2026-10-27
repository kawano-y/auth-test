package com.example.authtest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.authtest.domain.DepartmentRepository;
import com.example.authtest.domain.UserRepository;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * ユーザー登録画面。登録の中身（Cognito に渡す値など）は AdminUserApiTest で見ているので、
 * ここでは成功時のリダイレクトと、エラー時にエラー画面ではなくフォームが再表示されることを確認する。
 * Cognito のクライアントはモックにして、AWS には繋がない。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserPageTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	CognitoIdentityProviderClient cognitoClient;

	@Autowired
	DepartmentRepository departmentRepository;

	@Autowired
	UserRepository userRepository;

	private Long sales1Id;

	@BeforeEach
	void setUp() {
		sales1Id = departmentRepository.findByName("営業1課").orElseThrow().getId();
	}

	@Test
	void フォームに部署のプルダウンが出る() throws Exception {
		mockMvc.perform(get("/admin/users/new").with(admin()))
			.andExpect(status().isOk())
			.andExpect(view().name("admin/users/new"))
			.andExpect(content().string(containsString("営業1課")))
			.andExpect(content().string(containsString("営業2課")))
			.andExpect(content().string(containsString("管理部")));
	}

	@Test
	void 登録できたら完了メッセージを付けてフォームへリダイレクト() throws Exception {
		AdminCreateUserResponse response = AdminCreateUserResponse.builder()
			.user(UserType.builder().attributes(AttributeType.builder().name("sub").value("new-user-sub").build())
				.build())
			.build();
		when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(response);

		postUser("sales1@example.com", "営業1課ユーザー", String.valueOf(sales1Id))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/admin/users/new"))
			.andExpect(flash().attribute("message", containsString("sales1@example.com を登録しました")));

		assertThat(userRepository.findByCognitoSub("new-user-sub")).isPresent();
	}

	@Test
	void 入力不正はフォームを再表示してCognitoを呼ばない() throws Exception {
		postUser("not-an-email", "", "")
			.andExpect(status().isOk())
			.andExpect(view().name("admin/users/new"))
			.andExpect(model().attributeHasFieldErrors("userForm", "email", "name", "departmentId"))
			.andExpect(content().string(containsString("メールアドレスの形式が正しくありません")))
			// 入力した値が残っている
			.andExpect(content().string(containsString("value=\"not-an-email\"")));

		verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
	}

	@Test
	void 存在しない部署はフォームを再表示してCognitoを呼ばない() throws Exception {
		postUser("sales1@example.com", "営業1課ユーザー", "9999")
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("userForm", "departmentId"))
			.andExpect(content().string(containsString("部署が存在しません")));

		verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
	}

	@Test
	void メール重複はフォームを再表示() throws Exception {
		when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
			.thenThrow(UsernameExistsException.builder().message("User account already exists").build());

		postUser("sales1@example.com", "営業1課ユーザー", String.valueOf(sales1Id))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("userForm", "email"))
			.andExpect(content().string(containsString("このメールアドレスは登録済みです")));

		assertThat(userRepository.count()).isEqualTo(1); // 起動時に入る管理者だけ
	}

	@Test
	void ADMIN以外は登録できない() throws Exception {
		mockMvc.perform(post("/admin/users").with(oidcLogin()).with(csrf())
				.param("email", "sales1@example.com")
				.param("name", "営業1課ユーザー")
				.param("departmentId", String.valueOf(sales1Id)))
			.andExpect(status().isForbidden());

		verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
	}

	private ResultActions postUser(String email, String name, String departmentId) throws Exception {
		return mockMvc.perform(post("/admin/users").with(admin()).with(csrf())
			.param("email", email)
			.param("name", name)
			.param("departmentId", departmentId));
	}

	private static OidcLoginRequestPostProcessor admin() {
		return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

}
