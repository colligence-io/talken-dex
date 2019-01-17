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

	public DexResponseBody(T data) {
		this.status = new DexResponseHeader(DexResultCodeEnum.OK, "OK");
		this.data = data;
	}

	public DexResponseBody(DexResultCodeEnum resultCode, String message, T data) {
		this.status = new DexResponseHeader(resultCode, message);
		this.data = data;
	}

	@JsonIgnore
	public HttpStatus getHttpStatus() {
		return status.getResultCode().getHttpStatus();
	}

	@Getter
	@Setter
	@JsonPropertyOrder({"success", "resultNo", "resultCode", "message"})
	public static class DexResponseHeader {
		private DexResultCodeEnum resultCode;
		private String message;

		public DexResponseHeader(DexResultCodeEnum resultCode, String message) {
			this.resultCode = resultCode;
			this.message = message;
		}

		public boolean isSuccess() {
			return !this.resultCode.getHttpStatus().isError();
		}

		public int getResultNo() {
			return this.resultCode.getNo();
		}
	}
}
