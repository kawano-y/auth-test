package com.example.authtest.service;

/**
 * 登録しようとしたユーザーの部署が存在しない。API は 400、画面（PR9）はフォームにメッセージを出す。
 */
public class DepartmentNotFoundException extends RuntimeException {

	public DepartmentNotFoundException(Long departmentId) {
		super("部署がありません: id=" + departmentId);
	}

}
