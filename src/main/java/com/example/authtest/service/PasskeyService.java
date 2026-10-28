package com.example.authtest.service;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListWebAuthnCredentialsRequest;

/**
 * ログインユーザーのパスキー登録状況を Cognito に問い合わせる。
 *
 * ListWebAuthnCredentials はユーザー本人のアクセストークンで認可される API なので、IAM の権限（AWS 認証情報）は使わない。
 * ただしアクセストークンに aws.cognito.signin.user.admin スコープが入っていないと NotAuthorizedException になる。
 */
@Service
public class PasskeyService {

	private final CognitoIdentityProviderClient cognitoClient;

	public PasskeyService(CognitoIdentityProviderClient cognitoClient) {
		this.cognitoClient = cognitoClient;
	}

	public boolean hasPasskey(String accessToken) {
		// 1件でもあれば登録済みなので、1件だけ取る
		ListWebAuthnCredentialsRequest request = ListWebAuthnCredentialsRequest.builder()
			.accessToken(accessToken)
			.maxResults(1)
			.build();
		return !cognitoClient.listWebAuthnCredentials(request).credentials().isEmpty();
	}

}
