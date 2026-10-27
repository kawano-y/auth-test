package com.example.authtest.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * JwtDecoder だけをモックにして、Bearer トークンを付けたリクエストを本物のフィルターチェーンに通す。
 * これで groups → ROLE_ の変換や /api/admin/** の制限も、実際の設定のまま確認できる。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTest {

	private static final String TOKEN = "dummy-token";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	JwtDecoder jwtDecoder;

	@Test
	void トークンなしは401() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void meはsubと変換後の権限を返す() throws Exception {
		givenToken(List.of("ADMIN"));

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sub").value("user-sub"))
			.andExpect(jsonPath("$.username").value("user-name"))
			// FACTOR_BEARER は Spring Security 7 が「Bearer トークンで認証した」ことを表すために自動で付ける権限
			.andExpect(jsonPath("$.authorities", containsInAnyOrder("ROLE_ADMIN", "FACTOR_BEARER")));
	}

	@Test
	void グループなしならROLEは付かない() throws Exception {
		givenToken(null);

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authorities", contains("FACTOR_BEARER")));
	}

	@Test
	void adminパスはADMINでなければ403() throws Exception {
		givenToken(null);

		mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminパスはADMINなら通る() throws Exception {
		givenToken(List.of("ADMIN"));

		// /api/admin/users は POST だけなので GET は 405 になる（403 でないことの確認）
		mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isMethodNotAllowed());
	}

	/** Cognito はグループに入っていないユーザーのトークンには cognito:groups を含めない */
	private void givenToken(List<String> groups) {
		Jwt.Builder builder = Jwt.withTokenValue(TOKEN)
			.header("alg", "RS256")
			.subject("user-sub")
			.claim("username", "user-name");
		if (groups != null) {
			builder.claim("cognito:groups", groups);
		}
		when(jwtDecoder.decode(TOKEN)).thenReturn(builder.build());
	}

}
