package com.example.authtest.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.authtest.service.EstimateDto;
import com.example.authtest.service.EstimateService;

/**
 * 見積 API。アクセストークンの sub を Service に渡すだけで、部署の絞り込みは Service 側で行う。
 */
@RestController
public class EstimateController {

	private final EstimateService estimateService;

	public EstimateController(EstimateService estimateService) {
		this.estimateService = estimateService;
	}

	@GetMapping("/api/estimates")
	public List<EstimateDto> list(@AuthenticationPrincipal Jwt jwt) {
		return estimateService.findAll(jwt.getSubject());
	}

	@GetMapping("/api/estimates/{id}")
	public EstimateDto detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return estimateService.findById(jwt.getSubject(), id);
	}

}
