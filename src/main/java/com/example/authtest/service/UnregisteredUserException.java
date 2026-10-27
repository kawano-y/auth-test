package com.example.authtest.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Cognito では認証できたが、users に登録されていない（sub が見つからない）。
 * 認証済みだが使う権限がない、という扱いで 403 にする。API でも画面でも同じ。
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnregisteredUserException extends RuntimeException {

	public UnregisteredUserException(String sub) {
		super("users に登録されていません: sub=" + sub);
	}

}
