package com.example.authtest.api;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.authtest.domain.User;
import com.example.authtest.service.AdminUserService;
import com.example.authtest.service.DepartmentNotFoundException;
import com.example.authtest.service.EmailAlreadyExistsException;

import jakarta.validation.Valid;

/**
 * 管理者によるユーザー登録 API。/api/admin/** なので ROLE_ADMIN のみ（SecurityConfig）。
 * エラーは ProblemDetail（RFC 9457、Content-Type: application/problem+json）で返す。
 * ここの @ExceptionHandler はこのコントローラーで起きた例外にだけ効く。
 */
@RestController
public class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@PostMapping("/api/admin/users")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		User user = adminUserService.register(request.email(), request.name(), request.departmentId());
		return UserResponse.from(user);
	}

	/** 入力不正（空欄・メール形式）は 400。どの項目が悪いかを errors に入れる */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleInvalid(MethodArgumentNotValidException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "入力内容に誤りがあります");
		Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
			.collect(Collectors.toMap(FieldError::getField,
				error -> String.valueOf(error.getDefaultMessage()), (first, second) -> first));
		problem.setProperty("errors", errors);
		return problem;
	}

	/** JSON として読めない（構文エラー、UTF-8 でない文字コードなど）も入力不正として 400 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
			"リクエストの JSON を読み取れません（UTF-8 の JSON で送ってください）");
	}

	@ExceptionHandler(DepartmentNotFoundException.class)
	ProblemDetail handleDepartmentNotFound(DepartmentNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(EmailAlreadyExistsException.class)
	ProblemDetail handleEmailAlreadyExists(EmailAlreadyExistsException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	public record UserResponse(Long id, String sub, String email, String name, Long departmentId,
			String departmentName) {

		static UserResponse from(User user) {
			return new UserResponse(user.getId(), user.getCognitoSub(), user.getEmail(), user.getName(),
				user.getDepartment().getId(), user.getDepartment().getName());
		}

	}

}
