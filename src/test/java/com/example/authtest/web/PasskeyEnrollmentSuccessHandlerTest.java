package com.example.authtest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.example.authtest.service.PasskeyService;

import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

/**
 * ログイン成功時の振り分け。Cognito には繋がず、PasskeyService とアクセストークンの保存先はモックにする。
 */
class PasskeyEnrollmentSuccessHandlerTest {

	private static final String ACCESS_TOKEN = "access-token";

	private final PasskeyService passkeyService = mock(PasskeyService.class);

	private final OAuth2AuthorizedClientRepository authorizedClientRepository = mock(OAuth2AuthorizedClientRepository.class);

	private final PasskeyEnrollmentSuccessHandler handler = new PasskeyEnrollmentSuccessHandler(passkeyService,
			authorizedClientRepository);

	private final OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
			new DefaultOAuth2User(List.of(), Map.of("sub", "user-sub"), "sub"), List.of(), "cognito");

	private final MockHttpServletRequest request = new MockHttpServletRequest();

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	@BeforeEach
	void setUp() {
		when(authorizedClientRepository.loadAuthorizedClient(eq("cognito"), eq(authentication), any()))
			.thenReturn(authorizedClient());
	}

	@Test
	void パスキー未登録ならパスキー追加へリダイレクト() throws Exception {
		when(passkeyService.hasPasskey(ACCESS_TOKEN)).thenReturn(false);

		handler.onAuthenticationSuccess(request, response, authentication);

		assertThat(response.getRedirectedUrl()).isEqualTo("/passkeys/add");
	}

	@Test
	void パスキー登録済みなら既定どおりに戻す() throws Exception {
		when(passkeyService.hasPasskey(ACCESS_TOKEN)).thenReturn(true);

		handler.onAuthenticationSuccess(request, response, authentication);

		// ログイン前に開こうとした URL がセッションに無いときの既定の戻り先
		assertThat(response.getRedirectedUrl()).isEqualTo("/");
	}

	@Test
	void 登録状況を取得できなければログインは止めずに既定どおりに戻す() throws Exception {
		when(passkeyService.hasPasskey(ACCESS_TOKEN))
			.thenThrow(NotAuthorizedException.builder().message("Access Token does not have required scopes").build());

		handler.onAuthenticationSuccess(request, response, authentication);

		assertThat(response.getRedirectedUrl()).isEqualTo("/");
	}

	private static OAuth2AuthorizedClient authorizedClient() {
		ClientRegistration registration = ClientRegistration.withRegistrationId("cognito")
			.clientId("test-client-id")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.authorizationUri("https://test.auth.example.com/oauth2/authorize")
			.tokenUri("https://test.auth.example.com/oauth2/token")
			.build();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, ACCESS_TOKEN,
				Instant.now(), Instant.now().plusSeconds(60));
		return new OAuth2AuthorizedClient(registration, "user-sub", accessToken);
	}

}
