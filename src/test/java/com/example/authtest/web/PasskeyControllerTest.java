package com.example.authtest.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * パスキーの追加。Cognito には繋がず、リダイレクト先の URL だけを確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasskeyControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void ログイン済みならCognitoのパスキー登録画面へリダイレクト() throws Exception {
		mockMvc.perform(get("/passkeys/add").with(oidcLogin()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("https://test.auth.example.com/passkeys/add"
				+ "?client_id=test-client-id&redirect_uri=http://localhost/"));
	}

	@Test
	void 未ログインならログイン開始URLへリダイレクト() throws Exception {
		mockMvc.perform(get("/passkeys/add"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/oauth2/authorization/cognito"));
	}

}
