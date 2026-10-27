package com.example.authtest.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.authtest.service.EstimateService;
import com.example.authtest.service.LoginUserService;

/**
 * 見積の画面。API（EstimateController）と同じ Service を使い、渡すのが ID トークンの sub になるだけ。
 * users にいない sub（403）や他部署の見積（404）は Service が例外を投げ、
 * 例外の @ResponseStatus のステータスで templates/error/403.html・404.html が表示される。
 */
@Controller
public class EstimatePageController {

	private final LoginUserService loginUserService;

	private final EstimateService estimateService;

	public EstimatePageController(LoginUserService loginUserService, EstimateService estimateService) {
		this.loginUserService = loginUserService;
		this.estimateService = estimateService;
	}

	@GetMapping("/estimates")
	public String list(@AuthenticationPrincipal OidcUser user, Model model) {
		model.addAttribute("loginUser", loginUserService.getInfoBySub(user.getSubject()));
		model.addAttribute("estimates", estimateService.findAll(user.getSubject()));
		return "estimates/list";
	}

	@GetMapping("/estimates/{id}")
	public String detail(@AuthenticationPrincipal OidcUser user, @PathVariable Long id, Model model) {
		model.addAttribute("estimate", estimateService.findById(user.getSubject(), id));
		return "estimates/detail";
	}

}
