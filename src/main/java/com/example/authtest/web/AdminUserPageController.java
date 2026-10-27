package com.example.authtest.web;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.authtest.domain.DepartmentRepository;
import com.example.authtest.domain.User;
import com.example.authtest.service.AdminUserService;
import com.example.authtest.service.DepartmentNotFoundException;
import com.example.authtest.service.EmailAlreadyExistsException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 管理者によるユーザー登録画面。/admin/** なので ROLE_ADMIN のみ（SecurityConfig）。
 * 登録は API（AdminUserController）と同じ AdminUserService で行う。
 * API と違い、入力不正・部署なし・メール重複はエラー画面に飛ばさず、BindingResult にエラーを入れてフォームを再表示する。
 */
@Controller
public class AdminUserPageController {

	private static final String VIEW = "admin/users/new";

	private final AdminUserService adminUserService;

	private final DepartmentRepository departmentRepository;

	public AdminUserPageController(AdminUserService adminUserService, DepartmentRepository departmentRepository) {
		this.adminUserService = adminUserService;
		this.departmentRepository = departmentRepository;
	}

	/** 部署のプルダウン用。どのハンドラーでもテンプレートを描画する前にモデルに入る */
	@ModelAttribute
	void departments(Model model) {
		model.addAttribute("departments", departmentRepository.findAll(Sort.by("id")));
	}

	@GetMapping("/admin/users/new")
	public String form(@ModelAttribute("userForm") UserForm form) {
		return VIEW;
	}

	/**
	 * 登録できたら、完了メッセージをフラッシュ属性に入れてフォームへリダイレクトする（PRG パターン）。
	 * リダイレクトしておけば、ブラウザの再読み込みで同じ POST が送り直されない。
	 */
	@PostMapping("/admin/users")
	public String create(@Valid @ModelAttribute("userForm") UserForm form, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		// 形式が不正なら Service（Cognito）は呼ばない
		if (bindingResult.hasErrors()) {
			return VIEW;
		}
		try {
			User user = adminUserService.register(form.getEmail(), form.getName(), form.getDepartmentId());
			redirectAttributes.addFlashAttribute("message", user.getEmail() + " を登録しました。招待メールが送信されます。");
			return "redirect:/admin/users/new";
		}
		catch (DepartmentNotFoundException e) {
			bindingResult.rejectValue("departmentId", "notFound", "部署が存在しません");
		}
		catch (EmailAlreadyExistsException e) {
			bindingResult.rejectValue("email", "duplicate", "このメールアドレスは登録済みです");
		}
		return VIEW;
	}

	/**
	 * フォームの入力値。th:field は getter / setter で値を読み書きするので、record ではなく普通のクラスにする。
	 * 部署を未選択にすると空文字が送られ、departmentId は null になる。
	 */
	public static class UserForm {

		@NotBlank(message = "メールアドレスを入力してください")
		@Email(message = "メールアドレスの形式が正しくありません")
		private String email;

		@NotBlank(message = "名前を入力してください")
		private String name;

		@NotNull(message = "部署を選択してください")
		private Long departmentId;

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Long getDepartmentId() {
			return departmentId;
		}

		public void setDepartmentId(Long departmentId) {
			this.departmentId = departmentId;
		}

	}

}
