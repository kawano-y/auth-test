package com.example.authtest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.authtest.domain.Department;
import com.example.authtest.domain.DepartmentRepository;
import com.example.authtest.domain.User;
import com.example.authtest.domain.UserRepository;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * 管理者によるユーザー登録。Cognito にユーザーを作り、返ってきた sub で users に保存する。
 * API（AdminUserController）と画面（PR9）の両方から使う。
 *
 * Cognito の呼び出し中に DB のトランザクションを開いたままにしないよう、メソッド全体には @Transactional を付けない
 * （findById と save はそれぞれ Spring Data 側のトランザクションで動く）。
 * Cognito の作成後に DB 保存が失敗すると Cognito にだけユーザーが残るが、この検証では扱わない。
 */
@Service
public class AdminUserService {

	private final CognitoIdentityProviderClient cognitoClient;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final String userPoolId;

	public AdminUserService(CognitoIdentityProviderClient cognitoClient, DepartmentRepository departmentRepository,
			UserRepository userRepository, @Value("${COGNITO_USER_POOL_ID}") String userPoolId) {
		this.cognitoClient = cognitoClient;
		this.departmentRepository = departmentRepository;
		this.userRepository = userRepository;
		this.userPoolId = userPoolId;
	}

	/**
	 * 入力値の形式チェック（空欄・メール形式）は呼び出し側で済ませておく。
	 *
	 * @throws DepartmentNotFoundException 部署が存在しない（Cognito は呼ばない）
	 * @throws EmailAlreadyExistsException メールアドレスが Cognito に登録済み
	 */
	public User register(String email, String name, Long departmentId) {
		// Cognito にユーザーを作ってから部署なしで失敗すると Cognito にだけ残るので、先に確かめる
		Department department = departmentRepository.findById(departmentId)
			.orElseThrow(() -> new DepartmentNotFoundException(departmentId));

		AdminCreateUserResponse response;
		try {
			// MessageAction を指定しないので、一時パスワード入りの招待メールが Cognito から送られる
			response = cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
				.userPoolId(userPoolId)
				.username(email)
				.userAttributes(
					attribute("email", email),
					attribute("email_verified", "true"))
				.build());
		}
		catch (UsernameExistsException e) {
			throw new EmailAlreadyExistsException(email, e);
		}

		String sub = response.user().attributes().stream()
			.filter(attribute -> attribute.name().equals("sub"))
			.map(AttributeType::value)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("AdminCreateUser のレスポンスに sub がありません"));
		return userRepository.save(new User(sub, email, name, department));
	}

	private static AttributeType attribute(String name, String value) {
		return AttributeType.builder().name(name).value(value).build();
	}

}
