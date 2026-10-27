package com.example.authtest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * アプリ側のユーザー。Cognito のユーザーとは cognito_sub（トークンの sub）で紐づける。
 * 初期データはなく、最初の管理者は起動時処理（PR4）、それ以外は登録 API（PR6）で入れる。
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String cognitoSub;

	private String email;

	private String name;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id")
	private Department department;

	/** JPA 用 */
	protected User() {
	}

	public User(String cognitoSub, String email, String name, Department department) {
		this.cognitoSub = cognitoSub;
		this.email = email;
		this.name = name;
		this.department = department;
	}

	public Long getId() {
		return id;
	}

	public String getCognitoSub() {
		return cognitoSub;
	}

	public String getEmail() {
		return email;
	}

	public String getName() {
		return name;
	}

	public Department getDepartment() {
		return department;
	}

}
