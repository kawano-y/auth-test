package com.example.authtest.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.authtest.domain.EstimateRepository;
import com.example.authtest.domain.User;

/**
 * 部署別の認可をここに集める。ログインユーザーの所属部署の見積だけを返す。
 * API（EstimateController）と画面（PR8）の両方から、トークンの sub を渡して使う。
 */
@Service
@Transactional(readOnly = true)
public class EstimateService {

	private final LoginUserService loginUserService;

	private final EstimateRepository estimateRepository;

	public EstimateService(LoginUserService loginUserService, EstimateRepository estimateRepository) {
		this.loginUserService = loginUserService;
		this.estimateRepository = estimateRepository;
	}

	public List<EstimateDto> findAll(String sub) {
		User user = loginUserService.getBySub(sub);
		return estimateRepository.findByDepartmentIdOrderById(user.getDepartment().getId()).stream()
			.map(EstimateDto::from)
			.toList();
	}

	/** 存在しない ID も他部署の ID も、同じ 404（EstimateNotFoundException） */
	public EstimateDto findById(String sub, Long id) {
		User user = loginUserService.getBySub(sub);
		return estimateRepository.findByIdAndDepartmentId(id, user.getDepartment().getId())
			.map(EstimateDto::from)
			.orElseThrow(() -> new EstimateNotFoundException(id));
	}

}
