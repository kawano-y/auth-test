package com.example.authtest.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/admin/users のリクエスト。ここでは形式だけを見て、部署の存在確認は Service で行う。
 */
public record CreateUserRequest(
		@NotBlank @Email String email,
		@NotBlank String name,
		@NotNull Long departmentId) {
}
