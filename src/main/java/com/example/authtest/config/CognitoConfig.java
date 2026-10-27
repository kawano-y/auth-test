package com.example.authtest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
public class CognitoConfig {

	/**
	 * Cognito の管理 API（AdminCreateUser）を呼ぶクライアント。
	 * 認証情報は指定せず、AWS SDK のデフォルトのクレデンシャルチェーン
	 * （環境変数 → AWS_PROFILE / ~/.aws の設定 → …）から取る。取りに行くのは最初の呼び出し時。
	 */
	@Bean
	CognitoIdentityProviderClient cognitoIdentityProviderClient(@Value("${COGNITO_REGION}") String region) {
		return CognitoIdentityProviderClient.builder()
			.region(Region.of(region))
			.build();
	}

}
