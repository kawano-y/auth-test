package com.example.authtest.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 見積。所属部署（department）が同じユーザーだけが参照できる（認可は PR5 の Service 層で行う）。
 */
@Entity
@Table(name = "estimates")
public class Estimate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;

	/** 金額（円） */
	private long amount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id")
	private Department department;

	private LocalDateTime createdAt;

	/** JPA 用 */
	protected Estimate() {
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public long getAmount() {
		return amount;
	}

	public Department getDepartment() {
		return department;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

}
