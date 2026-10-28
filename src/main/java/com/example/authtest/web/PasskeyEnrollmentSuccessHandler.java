package com.example.authtest.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.authtest.service.PasskeyService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;

/**
 * 画面ログイン（oauth2Login）の成功時の処理。パスキー未登録のユーザーだけ、アプリの /passkeys/add
 * （PasskeyController。そこから Cognito のパスキー登録画面へ）にリダイレクトする。登録済みなら既定どおり元の URL に戻す。
 * 登録画面からはトップに戻るので、ログイン前に開こうとした URL には戻らない。
 * 登録しないで戻ったユーザーは、次のログインでもまた登録画面に送られる。
 */
@Component
public class PasskeyEnrollmentSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(PasskeyEnrollmentSuccessHandler.class);

	/** oauth2Login の既定の成功時処理（ログイン前に開こうとした URL へ戻す） */
	private final AuthenticationSuccessHandler defaultHandler = new SavedRequestAwareAuthenticationSuccessHandler();

	private final PasskeyService passkeyService;

	private final OAuth2AuthorizedClientRepository authorizedClientRepository;

	public PasskeyEnrollmentSuccessHandler(PasskeyService passkeyService,
			OAuth2AuthorizedClientRepository authorizedClientRepository) {
		this.passkeyService = passkeyService;
		this.authorizedClientRepository = authorizedClientRepository;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		// oauth2Login の成功時にしか使わないので、必ず OAuth2AuthenticationToken が来る
		OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
		if (!hasPasskey(token, request)) {
			// Cognito の登録画面の URL は PasskeyController が組み立てる
			response.sendRedirect(request.getContextPath() + "/passkeys/add");
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
