package com.example.authtest.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** トークンの sub からユーザーを引く。見つからなければ 403 にする（PR5） */
	Optional<User> findByCognitoSub(String cognitoSub);

}
