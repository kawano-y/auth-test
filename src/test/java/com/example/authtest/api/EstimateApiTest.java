package com.example.authtest.api;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.authtest.domain.DepartmentRepository;
import com.example.authtest.domain.EstimateRepository;
import com.example.authtest.domain.User;
import com.example.authtest.domain.UserRepository;

/**
 * 見積 API の部署別認可。営業1課のユーザーをテスト内で users に入れ、sub ごとにトークンを用意する。
 * テストはトランザクション内で動き、終わるとロールバックされる（MockMvc も同じスレッドなので、入れたユーザーが見える）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstimateApiTest {

	private static final String SALES1_TOKEN = "sales1-token";
	private static final String ADMIN_TOKEN = "admin-token";
	private static final String UNREGISTERED_TOKEN = "unregistered-token";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	JwtDecoder jwtDecoder;

	@Autowired
	DepartmentRepository departmentRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	EstimateRepository estimateRepository;

	@BeforeEach
	void setUp() {
		userRepository.save(new User("sales1-sub", "sales1@example.com", "営業1課ユーザー",
			departmentRepository.findByName("営業1課").orElseThrow()));

		givenToken(SALES1_TOKEN, "sales1-sub");
		// 起動時処理で管理部に登録される管理者（src/test/resources/application.properties の ADMIN_SUB）
		givenToken(ADMIN_TOKEN, "test-admin-sub");
		givenToken(UNREGISTERED_TOKEN, "unregistered-sub");
	}

	@Test
	void 一覧は自部署の見積だけ() throws Exception {
		mockMvc.perform(get("/api/estimates").header("Authorization", "Bearer " + SALES1_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[*].departmentName", everyItem(is("営業1課"))));
	}

	@Test
	void 管理部の管理者は一覧が空() throws Exception {
		mockMvc.perform(get("/api/estimates").header("Authorization", "Bearer " + ADMIN_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void 自部署の見積は詳細を取れる() throws Exception {
		Long id = estimateIdOf("営業1課");

		mockMvc.perform(get("/api/estimates/{id}", id).header("Authorization", "Bearer " + SALES1_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.departmentName").value("営業1課"));
	}

	@Test
	void 他部署の見積は404() throws Exception {
		Long id = estimateIdOf("営業2課");

		mockMvc.perform(get("/api/estimates/{id}", id).header("Authorization", "Bearer " + SALES1_TOKEN))
			.andExpect(status().isNotFound());
	}

	@Test
	void 存在しない見積も404() throws Exception {
		mockMvc.perform(get("/api/estimates/{id}", 9999).header("Authorization", "Bearer " + SALES1_TOKEN))
			.andExpect(status().isNotFound());
	}

	@Test
	void usersにいないsubは403() throws Exception {
		mockMvc.perform(get("/api/estimates").header("Authorization", "Bearer " + UNREGISTERED_TOKEN))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/estimates/{id}", estimateIdOf("営業1課"))
				.header("Authorization", "Bearer " + UNREGISTERED_TOKEN))
			.andExpect(status().isForbidden());
	}

	@Test
	void トークンなしは401() throws Exception {
		mockMvc.perform(get("/api/estimates"))
			.andExpect(status().isUnauthorized());
	}

	private Long estimateIdOf(String departmentName) {
		Long departmentId = departmentRepository.findByName(departmentName).orElseThrow().getId();
		return estimateRepository.findByDepartmentIdOrderById(departmentId).getFirst().getId();
	}

	private void givenToken(String token, String sub) {
		Jwt jwt = Jwt.withTokenValue(token)
			.header("alg", "RS256")
			.subject(sub)
			.build();
		when(jwtDecoder.decode(token)).thenReturn(jwt);
	}

}
