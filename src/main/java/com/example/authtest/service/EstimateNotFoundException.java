package com.example.authtest.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 見積が存在しない、または他部署の見積。
 * 他部署の場合も 403 ではなく 404 にして、その ID の見積が存在すること自体を隠す。
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EstimateNotFoundException extends RuntimeException {

	public EstimateNotFoundException(Long id) {
		super("見積がありません: id=" + id);
	}

}
