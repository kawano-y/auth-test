package com.example.authtest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/**
	 * API 用（/api/**）。Authorization: Bearer のアクセストークン（JWT）を検証する。
	 * セッションは作らず、未認証は 401、権限不足は 403。
	 */
	@Bean
	@Order(1)
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/api/**")
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Cookie を使わないので CSRF 対策は不要
			.csrf(csrf -> csrf.disable());
		return http.build();
	}

	/**
	 * 画面用（/api/** 以外）。PR7 で oauth2Login に置き換えるまでは、エラー画面以外すべて拒否する。
	 * /error を許可しておかないと、API の 404 などがエラー画面へ転送されたときに 403 になってしまう。
	 */
	@Bean
	@Order(2)
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/error").permitAll()
				.anyRequest().denyAll());
		return http.build();
	}

	/**
	 * アクセストークンの cognito:groups（例: ["ADMIN"]）を ROLE_ADMIN などの権限に変換する。
	 * 既定ではスコープ（scope クレーム）が SCOPE_xxx の権限になるが、それは使わない。
	 */
	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter groupsConverter = new JwtGrantedAuthoritiesConverter();
		groupsConverter.setAuthoritiesClaimName("cognito:groups");
		groupsConverter.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(groupsConverter);
		return converter;
	}

}
