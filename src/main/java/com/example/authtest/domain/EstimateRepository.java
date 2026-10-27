package com.example.authtest.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 見積は必ず部署で絞り込んで取得する。部署を指定しない findAll / findById は使わない。
 */
public interface EstimateRepository extends JpaRepository<Estimate, Long> {

	List<Estimate> findByDepartmentIdOrderById(Long departmentId);

	/** 他部署の見積なら空になる。存在を隠すため、呼び出し側では「存在しない」と同じ 404 にする（PR5） */
	Optional<Estimate> findByIdAndDepartmentId(Long id, Long departmentId);

}
