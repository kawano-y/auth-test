package com.example.authtest.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.authtest.domain.User;
import com.example.authtest.domain.UserRepository;

/**
 * トークンの sub から、ログイン中のユーザー（users の行）を引く。
 * API ではアクセストークン、画面（PR8）では ID トークンの sub を渡す。
 */
@Service
@Transactional(readOnly = true)
public class LoginUserService {

	private final UserRepository userRepository;

	public LoginUserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/** users にいなければ 403（UnregisteredUserException） */
	public User getBySub(String sub) {
		return userRepository.findByCognitoSub(sub)
			.orElseThrow(() -> new UnregisteredUserException(sub));
	}

}
