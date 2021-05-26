package io.talken.dex.api.controller;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import org.springframework.http.HttpStatus;

/**
 * General DTO wrapper for JSON Serializing
 * hide unnecessary fields
 *
 * @param <T> the type parameter
 */
@Data
public class DexResponseBody<T> {
	private DexResponseHeader status;
	private T data;
	private HttpStatus httpStatus;

	private static final int OK = HttpStatus.OK.value();

    /**
     * Instantiates a new Dex response body.
     *
     * @param resultCode the result code
     * @param message    the message
     * @param httpStatus the http status
     * @param data       the data
     */
    public DexResponseBody(int resultCode, String message, HttpStatus httpStatus, T data) {
		this.status = new DexResponseHeader(resultCode, message);
		this.httpStatus = httpStatus;
		this.data = data;
	}

    /**
     * Instantiates a new Dex response body.
     *
     * @param data the data
     */
    public DexResponseBody(T data) {
		this(OK, "OK", HttpStatus.OK, data);
	}

    /**
     * Gets http status.
     *
     * @return the http status
     */
    @JsonIgnore
	public HttpStatus getHttpStatus() {
		return this.httpStatus;
	}

    /**
     * The type Dex response header.
     */
    @Data
	@JsonPropertyOrder({"success", "resultNo", "resultCode", "message"})
	public static class DexResponseHeader {
		private int resultCode;
		private String message;

        /**
         * Instantiates a new Dex response header.
         *
         * @param resultCode the result code
         * @param message    the message
         */
        public DexResponseHeader(int resultCode, String message) {
			this.resultCode = resultCode;
			this.message = message;
		}

        /**
         * Is success boolean.
         *
         * @return the boolean
         */
        public boolean isSuccess() {
			return this.resultCode == OK;
		}
	}
}
