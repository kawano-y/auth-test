package com.example.authtest.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.authtest.domain.UserRepository;

/**
 * アプリ全体を起動し、起動時処理で管理者が users に入っていることを確認する。
 * sub は src/test/resources/application.properties の ADMIN_SUB（test-admin-sub）。
 */
@SpringBootTest
@Transactional
class AdminUserInitializerTest {

	@Autowired
	AdminUserInitializer initializer;

	@Autowired
	UserRepository userRepository;

	@Test
	void 起動すると管理者が管理部に登録されている() {
		assertThat(userRepository.findByCognitoSub("test-admin-sub"))
			.hasValueSatisfying(user -> {
				assertThat(user.getEmail()).isEqualTo("admin@example.com");
				assertThat(user.getName()).isEqualTo("管理者");
				assertThat(user.getDepartment().getName()).isEqualTo("管理部");
			});
	}

	@Test
	void もう一度動かしても重複登録しない() {
		initializer.run(new DefaultApplicationArguments());

		assertThat(userRepository.count()).isEqualTo(1);
	}

}
