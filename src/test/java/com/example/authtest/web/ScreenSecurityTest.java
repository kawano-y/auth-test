package com.example.authtest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.authtest.TestClientRegistrationConfig;

/**
 * 画面側のチェーン（oauth2Login）。Cognito には繋がず、
 * ログイン済みの状態は spring-security-test の oidcLogin() で作る。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScreenSecurityTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	GrantedAuthoritiesMapper idTokenGroupsMapper;

	@Test
	void 未ログインならログイン開始URLへリダイレクト() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/oauth2/authorization/cognito"));
	}

	@Test
	void ログイン開始はCognitoの認可エンドポイントへPKCE付きでリダイレクト() throws Exception {
		MvcResult result = mockMvc.perform(get("/oauth2/authorization/cognito"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String location = result.getResponse().getRedirectedUrl();
		assertThat(location)
			.startsWith(TestClientRegistrationConfig.AUTHORIZATION_URI)
			.contains("response_type=code")
			.contains("client_id=test-client-id")
			.contains("redirect_uri=http://localhost/login/oauth2/code/cognito")
			// クライアントシークレットなしなので PKCE が使われる
			.contains("code_challenge=")
			.contains("code_challenge_method=S256");
	}

	@Test
	void ログイン済みならトップは見積一覧へリダイレクト() throws Exception {
		mockMvc.perform(get("/").with(oidcLogin()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/estimates"));
	}

	@Test
	void IDトークンのgroupsをROLEに変換する() {
		assertThat(mapGroups(List.of("ADMIN"))).containsExactly("ROLE_ADMIN");
		assertThat(mapGroups(null)).isEmpty();
	}

	@Test
	void adminパスはADMINでなければ403() throws Exception {
		mockMvc.perform(get("/admin/users/new").with(oidcLogin()))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminパスはADMINなら通る() throws Exception {
		mockMvc.perform(get("/admin/users/new")
				.with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
			.andExpect(status().isOk());
	}

	@Test
	void ログアウトするとCognitoのlogoutへリダイレクト() throws Exception {
		mockMvc.perform(post("/logout").with(oidcLogin()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("https://test.auth.example.com/logout"
				+ "?client_id=test-client-id&logout_uri=http://localhost/"));
	}

	@Test
	void CSRFトークンなしのPOSTは403() throws Exception {
		mockMvc.perform(post("/logout").with(oidcLogin()))
			.andExpect(status().isForbidden());
	}

	/** ログイン時に oauth2Login が渡すのと同じ形（OidcUserAuthority）で変換器を呼ぶ */
	private List<String> mapGroups(List<String> groups) {
		OidcIdToken.Builder idToken = OidcIdToken.withTokenValue("id-token")
			.subject("user-sub")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60));
		if (groups != null) {
			idToken.claims(claims -> claims.putAll(Map.of("cognito:groups", groups)));
		}
		List<GrantedAuthority> input = List.of(new OidcUserAuthority(idToken.build()));
		return idTokenGroupsMapper.mapAuthorities(input).stream()
			.map(GrantedAuthority::getAuthority)
			.toList();
	}

}
