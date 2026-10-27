package com.example.authtest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * テスト用のクライアント登録。application.yml の issuer-uri から作ると起動時に Cognito へ
 * OIDC discovery の通信が発生するので、エンドポイントを直接書いた登録に差し替える。
 * ClientRegistrationRepository の Bean があると、Spring Boot の自動設定（discovery する方）は動かない。
 * src/test 配下の @Configuration なので、テストのときだけコンポーネントスキャンで読み込まれる。
 */
@Configuration
public class TestClientRegistrationConfig {

	public static final String AUTHORIZATION_URI = "https://test.auth.example.com/oauth2/authorize";

	@Bean
	ClientRegistrationRepository clientRegistrationRepository() {
		ClientRegistration cognito = ClientRegistration.withRegistrationId("cognito")
			.clientId("test-client-id")
			.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.scope("openid", "email")
			.authorizationUri(AUTHORIZATION_URI)
			.tokenUri("https://test.auth.example.com/oauth2/token")
			.userInfoUri("https://test.auth.example.com/oauth2/userInfo")
			.jwkSetUri("https://cognito-idp.ap-northeast-1.amazonaws.com/ap-northeast-1_dummy/.well-known/jwks.json")
			.issuerUri("https://cognito-idp.ap-northeast-1.amazonaws.com/ap-northeast-1_dummy")
			.userNameAttributeName("email")
			.build();
		return new InMemoryClientRegistrationRepository(cognito);
	}

}
