package com.example.authtest.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.authtest.domain.DepartmentRepository;
import com.example.authtest.domain.EstimateRepository;
import com.example.authtest.domain.User;
import com.example.authtest.domain.UserRepository;

/**
 * 見積画面。部署別認可そのものは EstimateApiTest で見ているので、ここでは画面に正しく出ること、
 * 403・404 になること、エラー画面のテンプレートが表示できることを確認する。
 * ログイン状態は oidcLogin() で作り、ID トークンの sub を users の cognito_sub に合わせる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstimatePageTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DepartmentRepository departmentRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	EstimateRepository estimateRepository;

	@BeforeEach
	void setUp() {
		userRepository.save(new User("sales1-sub", "sales1@example.com", "営業1課ユーザー",
			departmentRepository.findByName("営業1課").orElseThrow()));
	}

	@Test
	void 一覧に自部署の見積とログインユーザーが出る() throws Exception {
		mockMvc.perform(get("/estimates").with(loginAs("sales1-sub")))
			.andExpect(status().isOk())
			.andExpect(view().name("estimates/list"))
			.andExpect(model().attribute("estimates", hasSize(3)))
			.andExpect(content().string(containsString("営業1課ユーザー")))
			.andExpect(content().string(containsString("A社 基幹システム改修")))
			.andExpect(content().string(containsString("12,000,000")))
			// 営業2課の見積は出ない
			.andExpect(content().string(not(containsString("D社 ECサイト構築"))));
	}

	@Test
	void 管理部の管理者は一覧が空() throws Exception {
		// 起動時処理で管理部に登録される管理者（src/test/resources/application.properties の ADMIN_SUB）
		mockMvc.perform(get("/estimates").with(loginAs("test-admin-sub")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("管理部")))
			.andExpect(content().string(containsString("見積はありません")));
	}

	@Test
	void 自部署の見積は詳細が出る() throws Exception {
		Long id = estimateIdOf("営業1課");

		mockMvc.perform(get("/estimates/{id}", id).with(loginAs("sales1-sub")))
			.andExpect(status().isOk())
			.andExpect(view().name("estimates/detail"))
			.andExpect(content().string(containsString("A社 基幹システム改修")))
			.andExpect(content().string(containsString("2026-09-01 10:00")));
	}

	@Test
	void 他部署の見積と存在しない見積は404() throws Exception {
		mockMvc.perform(get("/estimates/{id}", estimateIdOf("営業2課")).with(loginAs("sales1-sub")))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/estimates/{id}", 9999).with(loginAs("sales1-sub")))
			.andExpect(status().isNotFound());
	}

	@Test
	void usersにいないsubは403() throws Exception {
		mockMvc.perform(get("/estimates").with(loginAs("unregistered-sub")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/estimates/{id}", estimateIdOf("営業1課")).with(loginAs("unregistered-sub")))
			.andExpect(status().isForbidden());
	}

	/**
	 * MockMvc はサーブレットコンテナを動かさないので、403・404 のときに /error への転送が起きない。
	 * そこで転送後のリクエスト（ステータスがリクエスト属性に入った /error）を直接作り、テンプレートが出ることを見る。
	 */
	@Test
	void エラー画面は403と404のテンプレートを使う() throws Exception {
		mockMvc.perform(get("/error").with(loginAs("sales1-sub")).accept(MediaType.TEXT_HTML)
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403))
			.andExpect(status().isForbidden())
			.andExpect(content().string(containsString("アクセスできません（403）")));
		mockMvc.perform(get("/error").with(loginAs("sales1-sub")).accept(MediaType.TEXT_HTML)
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
			.andExpect(status().isNotFound())
			.andExpect(content().string(containsString("見つかりません（404）")));
	}

	private static OidcLoginRequestPostProcessor loginAs(String sub) {
		return oidcLogin().idToken(token -> token.subject(sub));
	}

	private Long estimateIdOf(String departmentName) {
		Long departmentId = departmentRepository.findByName(departmentName).orElseThrow().getId();
		return estimateRepository.findByDepartmentIdOrderById(departmentId).getFirst().getId();
	}

}
