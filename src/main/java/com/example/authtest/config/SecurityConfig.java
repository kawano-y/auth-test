package com.example.authtest.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

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
	 * 画面用（/api/** 以外）。未ログインなら Cognito のマネージドログインへリダイレクトする（oauth2Login）。
	 * ログイン後のトークンはサーバーのセッションに置き、ブラウザには JSESSIONID の Cookie だけを渡す。
	 * Cookie でログイン状態を持つので CSRF 対策は有効のまま（POST のフォームにはトークンが自動で入る）。
	 * /error を許可しておかないと、404 などがエラー画面へ転送されたときにログインへ飛ばされてしまう。
	 */
	@Bean
	@Order(2)
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
			LogoutSuccessHandler cognitoLogoutSuccessHandler) throws Exception {
		http
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/error").permitAll()
				.requestMatchers("/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			// GrantedAuthoritiesMapper は Bean として登録しておけば oauth2Login が使う（下の idTokenGroupsMapper）
			.oauth2Login(withDefaults())
			// POST /logout でセッションを破棄し、Cognito 側のログアウトへリダイレクトする
			.logout(logout -> logout.logoutSuccessHandler(cognitoLogoutSuccessHandler));
		return http.build();
	}

	/**
	 * 画面ログイン時に、ID トークンの cognito:groups（例: ["ADMIN"]）を ROLE_ADMIN などの権限に変換する。
	 * 既定の OIDC_USER や SCOPE_xxx の権限は使わないので、API 側と同じく groups 由来の ROLE_ だけにする。
	 */
	@Bean
	GrantedAuthoritiesMapper idTokenGroupsMapper() {
		return authorities -> authorities.stream()
			.filter(OidcUserAuthority.class::isInstance)
			.map(OidcUserAuthority.class::cast)
			.flatMap(authority -> groupsOf(authority).stream())
			.map(group -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + group))
			.toList();
	}

	/**
	 * Cognito のログアウト。アプリのセッションを消すだけだと Cognito 側のログイン状態（Cookie）が残り、
	 * 次のログインでパスワードを聞かれずに入れてしまうので、Cognito の /logout にもリダイレクトする。
	 * Cognito は OIDC 標準の end_session_endpoint を公開していないため、URL は自分で組み立てる。
	 * logout_uri は Cognito の「許可されているサインアウト URL」と完全一致させる（http://localhost:8080/）。
	 */
	@Bean
	LogoutSuccessHandler cognitoLogoutSuccessHandler(@Value("${COGNITO_DOMAIN}") String cognitoDomain,
			@Value("${COGNITO_CLIENT_ID}") String clientId) {
		return (request, response, authentication) -> {
			String logoutUri = ServletUriComponentsBuilder.fromContextPath(request).path("/").toUriString();
			String cognitoLogoutUrl = UriComponentsBuilder.fromUriString(cognitoDomain)
				.path("/logout")
				.queryParam("client_id", clientId)
				.queryParam("logout_uri", logoutUri)
				.encode()
				.toUriString();
			response.sendRedirect(cognitoLogoutUrl);
		};
	}

	/** Cognito はグループに入っていないユーザーのトークンには cognito:groups を含めない */
	private static List<String> groupsOf(OidcUserAuthority authority) {
		List<String> groups = authority.getIdToken().getClaimAsStringList("cognito:groups");
		return groups != null ? groups : List.of();
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
