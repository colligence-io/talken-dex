package io.colligence.talken.dex.service.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"stackTrace", "cause", "localizedMessage", "suppressed"})
public class APIError extends Throwable {
	private static final long serialVersionUID = -9087245376865927948L;
	private String apiName;
	private String code;
	private String message;
	private Object rawResult;

	public APIError(String apiName, String code, String message, Object rawResult) {
		this.apiName = apiName;
		this.code = code;
		this.message = message;
		this.rawResult = rawResult;
	}

	public String getApiName() {
		return apiName;
	}

	public String getCode() {
		return code;
	}

	@Override
	public String getMessage() {
		return message;
	}

	public Object getRawResult() {
		return rawResult;
	}

	@Override
	public String toString() {
		return "APIError{" +
				"apiName='" + apiName + '\'' +
				", code='" + code + '\'' +
				", message='" + message + '\'' +
				", rawResult=" + rawResult +
				'}';
	}
}
