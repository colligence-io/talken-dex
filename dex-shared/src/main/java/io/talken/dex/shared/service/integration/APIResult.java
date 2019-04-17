package io.talken.dex.shared.service.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties({"cause", "data"})
public class APIResult<T> {
	private String apiName;
	private boolean isSuccess = false;
	private int responseCode;
	private String errorCode;
	private String errorMessage;
	private Throwable cause;
	private T data;

	public APIResult(String apiName) {
		this.apiName = apiName;
	}

	public void setResponseCode(int responseCode) {
		this.responseCode = responseCode;
	}

	public void setData(T data) {
		this.data = data;
	}

	public void setSuccess(boolean isSuccess) {
		this.isSuccess = isSuccess;
	}

	public void setError(String errorCode, String errorMessage) {
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		this.isSuccess = false;
	}

	public void setException(Throwable cause) {
		this.cause = cause;
		setError(cause.getClass().getSimpleName(), cause.getMessage());
	}

	@Override
	public String toString() {
		return "APIResult{" +
				"apiName='" + apiName + '\'' +
				", isSuccess=" + isSuccess +
				", responseCode='" + responseCode + '\'' +
				", errorCode='" + errorCode + '\'' +
				", errorMessage='" + errorMessage + '\'' +
				", cause=" + cause +
				", data=" + data +
				'}';
	}
}