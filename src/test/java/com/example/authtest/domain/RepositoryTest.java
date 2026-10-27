package com.example.authtest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * JPA 部分だけを起動し、data.sql の初期データとリポジトリのクエリを確認する。
 * 各テストはトランザクション内で動き、終わるとロールバックされる。
 */
@DataJpaTest
class RepositoryTest {

	private static final long SALES1 = 1L;
	private static final long SALES2 = 2L;
	private static final long ADMIN_DEPT = 3L;

	@Autowired
	DepartmentRepository departmentRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	EstimateRepository estimateRepository;

	@Test
	void 部署は3つ() {
		List<String> names = departmentRepository.findAll().stream().map(Department::getName).toList();
		assertThat(names).containsExactly("営業1課", "営業2課", "管理部");
	}

	@Test
	void 見積は部署で絞り込める() {
		assertThat(estimateRepository.findByDepartmentIdOrderById(SALES1))
			.isNotEmpty()
			.allSatisfy(estimate -> assertThat(estimate.getDepartment().getId()).isEqualTo(SALES1));
		assertThat(estimateRepository.findByDepartmentIdOrderById(SALES2))
			.isNotEmpty()
			.allSatisfy(estimate -> assertThat(estimate.getDepartment().getId()).isEqualTo(SALES2));
		assertThat(estimateRepository.findByDepartmentIdOrderById(ADMIN_DEPT)).isEmpty();
	}

	@Test
	void 他部署の見積はIDを指定しても取れない() {
		Estimate sales2Estimate = estimateRepository.findByDepartmentIdOrderById(SALES2).getFirst();

		assertThat(estimateRepository.findByIdAndDepartmentId(sales2Estimate.getId(), SALES2)).isPresent();
		assertThat(estimateRepository.findByIdAndDepartmentId(sales2Estimate.getId(), SALES1)).isEmpty();
	}

	@Test
	void 初期ユーザーはいない() {
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void ユーザーをsubで引ける() {
		Department sales1 = departmentRepository.findById(SALES1).orElseThrow();
		userRepository.save(new User("test-sub", "user@example.com", "テストユーザー", sales1));

		assertThat(userRepository.findByCognitoSub("test-sub"))
			.hasValueSatisfying(user -> {
				assertThat(user.getEmail()).isEqualTo("user@example.com");
				assertThat(user.getDepartment().getName()).isEqualTo("営業1課");
			});
		assertThat(userRepository.findByCognitoSub("unknown-sub")).isEmpty();
	}

}
