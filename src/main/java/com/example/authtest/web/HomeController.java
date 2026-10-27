package com.example.authtest.web;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ログイン確認用のトップ画面。ID トークンから取り出したユーザー情報と、変換後の権限を表示する。
 * PR8 で見積一覧（/estimates）へのリダイレクトに置き換える。
 */
@Controller
public class HomeController {

	@GetMapping("/")
	public String home(@AuthenticationPrincipal OidcUser user, Model model) {
		model.addAttribute("email", user.getEmail());
		model.addAttribute("sub", user.getSubject());
		model.addAttribute("authorities", user.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.toList());
		return "home";
	}

}
