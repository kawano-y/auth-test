package com.example.authtest.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.authtest.service.PasskeyService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;

/**
 * 画面ログイン（oauth2Login）の成功時の処理。パスキー未登録のユーザーだけ、Cognito のパスキー登録画面
 * （マネージドログインの /passkeys/add）へリダイレクトする。登録済みなら既定どおり元の URL に戻す。
 *
 * 登録画面からの戻り先（redirect_uri）は Cognito の「許可されているコールバック URL」に入っている必要があるので、
 * 元の URL ではなくトップ（http://localhost:8080/）に固定する。トップは見積一覧へリダイレクトする。
 * 登録しないで戻ったユーザーは、次のログインでもまた登録画面に送られる。
 */
@Component
public class PasskeyEnrollmentSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(PasskeyEnrollmentSuccessHandler.class);

	/** oauth2Login の既定の成功時処理（ログイン前に開こうとした URL へ戻す） */
	private final AuthenticationSuccessHandler defaultHandler = new SavedRequestAwareAuthenticationSuccessHandler();

	private final PasskeyService passkeyService;

	private final OAuth2AuthorizedClientRepository authorizedClientRepository;

	private final String cognitoDomain;

	private final String clientId;

	public PasskeyEnrollmentSuccessHandler(PasskeyService passkeyService,
			OAuth2AuthorizedClientRepository authorizedClientRepository,
			@Value("${COGNITO_DOMAIN}") String cognitoDomain, @Value("${COGNITO_CLIENT_ID}") String clientId) {
		this.passkeyService = passkeyService;
		this.authorizedClientRepository = authorizedClientRepository;
		this.cognitoDomain = cognitoDomain;
		this.clientId = clientId;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		if (authentication instanceof OAuth2AuthenticationToken token && !hasPasskey(token, request)) {
			String returnUri = ServletUriComponentsBuilder.fromContextPath(request).path("/").toUriString();
			String passkeyAddUrl = UriComponentsBuilder.fromUriString(cognitoDomain)
				.path("/passkeys/add")
				.queryParam("client_id", clientId)
				.queryParam("redirect_uri", returnUri)
				.encode()
				.toUriString();
			response.sendRedirect(passkeyAddUrl);
			return;
		}
		defaultHandler.onAuthenticationSuccess(request, response, authentication);
	}

	/**
	 * Cognito への問い合わせに失敗したとき（スコープ不足など）は、ログイン自体は止めずに登録済み扱いにする。
	 * oauth2Login は成功時処理を呼ぶ前にアクセストークンを保存しているので、ここで取り出せる。
	 */
	private boolean hasPasskey(OAuth2AuthenticationToken token, HttpServletRequest request) {
		OAuth2AuthorizedClient client = authorizedClientRepository
			.loadAuthorizedClient(token.getAuthorizedClientRegistrationId(), token, request);
		try {
			return passkeyService.hasPasskey(client.getAccessToken().getTokenValue());
		}
		catch (CognitoIdentityProviderException e) {
			log.warn("パスキーの登録状況を取得できませんでした: {}", e.getMessage());
			return true;
		}
	}

}
