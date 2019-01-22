package io.colligence.talken.dex.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
public class DexResponseBody<T> {
	private DexResponseHeader status;
	private T data;

	private static final int OK = 0;

	public DexResponseBody(T data) {
		this.status = new DexResponseHeader(OK, "OK");
		this.data = data;
	}

	public DexResponseBody(int resultCode, String message, T data) {
		this.status = new DexResponseHeader(resultCode, message);
		this.data = data;
	}

	@JsonIgnore
	public HttpStatus getHttpStatus() {
		return status.isSuccess() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
	}

	@Getter
	@Setter
	@JsonPropertyOrder({"success", "resultNo", "resultCode", "message"})
	public static class DexResponseHeader {
		private int resultCode;
		private String message;

		public DexResponseHeader(int resultCode, String message) {
			this.resultCode = resultCode;
			this.message = message;
		}

		public boolean isSuccess() {
			return this.resultCode == OK;
		}
	}
}
