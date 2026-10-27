package com.example.authtest.api;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証の確認用。トークンから取り出したユーザー情報と、変換後の権限を返す。
 */
@RestController
public class MeController {

	@GetMapping("/api/me")
	public MeResponse me(JwtAuthenticationToken authentication) {
		List<String> authorities = authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.toList();
		return new MeResponse(
			authentication.getToken().getSubject(),
			authentication.getToken().getClaimAsString("username"),
			authorities);
	}

	public record MeResponse(String sub, String username, List<String> authorities) {
	}

}
