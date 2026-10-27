package com.example.authtest.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * トップ画面。見積一覧へリダイレクトするだけ。
 * ログイン後は最初に開こうとした URL に戻るので、http://localhost:8080/ から入るとここを経由して /estimates になる。
 */
@Controller
public class HomeController {

	@GetMapping("/")
	public String home() {
		return "redirect:/estimates";
	}

}
