package com.example.authtest.service;

/**
 * 同じメールアドレスのユーザーが Cognito にすでにいる（UsernameExistsException）。
 * API は 409、画面（PR9）はフォームにメッセージを出す。
 */
public class EmailAlreadyExistsException extends RuntimeException {

	public EmailAlreadyExistsException(String email, Throwable cause) {
		super("このメールアドレスは登録済みです: " + email, cause);
	}

}
