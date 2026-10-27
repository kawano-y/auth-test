package com.example.authtest.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.authtest.domain.Department;
import com.example.authtest.domain.DepartmentRepository;
import com.example.authtest.domain.User;
import com.example.authtest.domain.UserRepository;

/**
 * 起動時に最初の管理者を users に登録する。
 * 管理者は Cognito コンソールで手動作成しているが、H2 は起動のたびに空になるので、毎回ここで入れ直す。
 * ApplicationRunner はアプリの起動完了直前（data.sql の投入後）に1回だけ呼ばれる。
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

	/** 管理者の所属部署。見積が0件なので、管理者の見積一覧は空になる */
	static final String ADMIN_DEPARTMENT_NAME = "管理部";

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final String sub;

	private final String email;

	private final String name;

	public AdminUserInitializer(DepartmentRepository departmentRepository, UserRepository userRepository,
			@Value("${app.admin.sub}") String sub,
			@Value("${app.admin.email}") String email,
			@Value("${app.admin.name}") String name) {
		this.departmentRepository = departmentRepository;
		this.userRepository = userRepository;
		this.sub = sub;
		this.email = email;
		this.name = name;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userRepository.findByCognitoSub(sub).isPresent()) {
			return;
		}
		Department department = departmentRepository.findByName(ADMIN_DEPARTMENT_NAME)
			.orElseThrow(() -> new IllegalStateException("部署がありません: " + ADMIN_DEPARTMENT_NAME));
		userRepository.save(new User(sub, email, name, department));
		log.info("最初の管理者を登録しました: sub={}, department={}", sub, department.getName());
	}

}
