package io.colligence.talken.dex.api;

import org.springframework.http.HttpStatus;

public enum DexResultCodeEnum {
	OK(0, HttpStatus.OK),
	NOT_FOUND(1, HttpStatus.NOT_FOUND),
	ERROR(2, HttpStatus.INTERNAL_SERVER_ERROR);

	private final int no;
	private final HttpStatus httpStatus;

	DexResultCodeEnum(int no, HttpStatus statusCode) {
		this.no = no;
		this.httpStatus = statusCode;
	}

	public int getNo() {
		return no;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
}
