package io.talken.dex.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * ResponseEntity Wrapper
 *
 * @param <T> the type parameter
 */
public class DexResponse<T> extends ResponseEntity<DexResponseBody<T>> {
    /**
     * Instantiates a new Dex response.
     *
     * @param body   the body
     * @param status the status
     */
    public DexResponse(DexResponseBody<T> body, HttpStatus status) {
		super(body, status);
	}

    /**
     * Build response dex response.
     *
     * @param <T>  the type parameter
     * @param data the data
     * @return the dex response
     */
    public static <T> DexResponse<T> buildResponse(T data) {
		return buildResponse(new DexResponseBody<>(data));
	}

    /**
     * Build response dex response.
     *
     * @param <T>          the type parameter
     * @param responseBody the response body
     * @return the dex response
     */
    public static <T> DexResponse<T> buildResponse(DexResponseBody<T> responseBody) {
		return new DexResponse<>(responseBody, responseBody.getHttpStatus());
	}
}
