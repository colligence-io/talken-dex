package io.colligence.talken.dex.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class DexResponse<T> extends ResponseEntity<DexResponseBody<T>> {
	public DexResponse(DexResponseBody<T> body, HttpStatus status) {
		super(body, status);
	}

	public static <T> DexResponse<T> buildResponse(T data) {
		return buildResponse(new DexResponseBody<>(data));
	}

	public static <T> DexResponse<T> buildResponse(DexResponseBody<T> responseBody) {
		return new DexResponse<>(responseBody, responseBody.getHttpStatus());
	}
}
