package com.example.authtest.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * パスキーの追加。Cognito のマネージドログインのパスキー登録画面（/passkeys/add）へリダイレクトするだけ。
 * ログイン直後の誘導（PasskeyEnrollmentSuccessHandler）と、画面の「パスキーを追加」リンクの両方がここを通る。
 *
 * パスキーは1人で複数登録できるので、端末側で消してしまったときもここから作り直せる
 * （端末側で消しても Cognito には登録が残るため、ログイン直後の誘導は出なくなる）。
 * Cognito の登録画面は、マネージドログインにログイン済み（Cognito 側の Cookie がある）ことが前提。
 * 登録画面からの戻り先（redirect_uri）は Cognito の「許可されているコールバック URL」に入っている必要があるので、
 * トップ（http://localhost:8080/）に固定する。トップは見積一覧へリダイレクトする。
 */
@Controller
public class PasskeyController {

	private final String cognitoDomain;

	private final String clientId;

	public PasskeyController(@Value("${COGNITO_DOMAIN}") String cognitoDomain,
			@Value("${COGNITO_CLIENT_ID}") String clientId) {
		this.cognitoDomain = cognitoDomain;
		this.clientId = clientId;
	}

	@GetMapping("/passkeys/add")
	public String add() {
		String returnUri = ServletUriComponentsBuilder.fromCurrentContextPath().path("/").toUriString();
		String passkeyAddUrl = UriComponentsBuilder.fromUriString(cognitoDomain)
			.path("/passkeys/add")
			.queryParam("client_id", clientId)
			.queryParam("redirect_uri", returnUri)
			.encode()
			.toUriString();
		return "redirect:" + passkeyAddUrl;
	}

}
