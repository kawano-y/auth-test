package com.example.authtest.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 部署。営業1課・営業2課・管理部の3つを data.sql で投入する。
 */
@Entity
@Table(name = "departments")
public class Department {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	/** JPA 用 */
	protected Department() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

}
