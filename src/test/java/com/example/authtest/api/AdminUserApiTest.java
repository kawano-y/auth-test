package com.example.authtest.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
 * ユーザー登録 API。Cognito のクライアントはモックにして、AWS には繋がない。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserApiTest {

	private static final String ADMIN_TOKEN = "admin-token";
	private static final String USER_TOKEN = "user-token";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	JwtDecoder jwtDecoder;

	@MockitoBean
	CognitoIdentityProviderClient cognitoClient;

	@Autowired
	DepartmentRepository departmentRepository;

	@Autowired
	UserRepository userRepository;

	private Long sales1Id;

	@BeforeEach
	void setUp() {
		givenToken(ADMIN_TOKEN, List.of("ADMIN"));
		givenToken(USER_TOKEN, List.of());
		sales1Id = departmentRepository.findByName("営業1課").orElseThrow().getId();
	}

	@Test
	void 登録するとCognitoに作成してusersに保存する() throws Exception {
		givenCognitoReturnsSub("new-user-sub");

		postUser(ADMIN_TOKEN, """
				{"email": "sales1@example.com", "name": "営業1課ユーザー", "departmentId": %d}
				""".formatted(sales1Id))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sub").value("new-user-sub"))
			.andExpect(jsonPath("$.email").value("sales1@example.com"))
			.andExpect(jsonPath("$.departmentName").value("営業1課"));

		ArgumentCaptor<AdminCreateUserRequest> captor = ArgumentCaptor.forClass(AdminCreateUserRequest.class);
		verify(cognitoClient).adminCreateUser(captor.capture());
		AdminCreateUserRequest request = captor.getValue();
		assertThat(request.userPoolId()).isEqualTo("ap-northeast-1_dummy");
		assertThat(request.username()).isEqualTo("sales1@example.com");
		assertThat(request.userAttributes()).containsExactlyInAnyOrder(
			attribute("email", "sales1@example.com"), attribute("email_verified", "true"));
		// 招待メールを送るため MessageAction は指定しない
		assertThat(request.messageAction()).isNull();

		assertThat(userRepository.findByCognitoSub("new-user-sub"))
			.hasValueSatisfying(user -> assertThat(user.getDepartment().getId()).isEqualTo(sales1Id));
	}

	@Test
	void 入力不正は400() throws Exception {
		postUser(ADMIN_TOKEN, """
				{"email": "not-an-email", "name": "", "departmentId": null}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.departmentId").exists());

		verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
	}

	@Test
	void 存在しない部署は400でCognitoを呼ばない() throws Exception {
		postUser(ADMIN_TOKEN, """
				{"email": "sales1@example.com", "name": "営業1課ユーザー", "departmentId": 9999}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
	}

	@Test
	void メール重複は409() throws Exception {
		when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
			.thenThrow(UsernameExistsException.builder().message("User account already exists").build());

		postUser(ADMIN_TOKEN, """
				{"email": "sales1@example.com", "name": "営業1課ユーザー", "departmentId": %d}
				""".formatted(sales1Id))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(userRepository.count()).isEqualTo(1); // 起動時に入る管理者だけ
	}

	@Test
	void ADMIN以外は403() throws Exception {
		postUser(USER_TOKEN, """
				{"email": "sales1@example.com", "name": "営業1課ユーザー", "departmentId": %d}
				""".formatted(sales1Id))
			.andExpect(status().isForbidden());

		verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
	}

	private ResultActions postUser(String token, String json) throws Exception {
		return mockMvc.perform(post("/api/admin/users")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	/** 実際の AdminCreateUser も、作成したユーザーの属性に sub を入れて返す */
	private void givenCognitoReturnsSub(String sub) {
		AdminCreateUserResponse response = AdminCreateUserResponse.builder()
			.user(UserType.builder().attributes(attribute("sub", sub)).build())
			.build();
		when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(response);
	}

	private void givenToken(String token, List<String> groups) {
		Jwt jwt = Jwt.withTokenValue(token)
			.header("alg", "RS256")
			.subject(token + "-sub")
			.claim("cognito:groups", groups)
			.build();
		when(jwtDecoder.decode(token)).thenReturn(jwt);
	}

	private static AttributeType attribute(String name, String value) {
		return AttributeType.builder().name(name).value(value).build();
	}

}
