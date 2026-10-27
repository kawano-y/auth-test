package com.example.authtest.service;

import java.time.LocalDateTime;

import com.example.authtest.domain.Estimate;

/**
 * 見積の表示用データ。API のレスポンスにも、画面（PR8）のテンプレートにもこのまま使う。
 * 部署名は遅延ロードなので、トランザクション内（Service の中）で詰めておく。
 */
public record EstimateDto(Long id, String title, long amount, String departmentName, LocalDateTime createdAt) {

	static EstimateDto from(Estimate estimate) {
		return new EstimateDto(
			estimate.getId(),
			estimate.getTitle(),
			estimate.getAmount(),
			estimate.getDepartment().getName(),
			estimate.getCreatedAt());
	}

}
