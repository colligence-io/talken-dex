package io.colligence.talken.dex.service.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import io.colligence.talken.common.util.JSONWriter;

public abstract class AbstractRestApiService {

	private static HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
	private static JsonFactory jsonFactory = new JacksonFactory();

	protected <T extends CodeMessageResponseInterface> APIResult<T> requestPost(String url, Class<T> responseClass) {
		return requestPost(url, null, null, responseClass);
	}

	protected <T extends CodeMessageResponseInterface> APIResult<T> requestPost(String url, Object requestBody, Class<T> responseClass) {
		return requestPost(url, null, requestBody, responseClass);
	}

	protected <T extends CodeMessageResponseInterface> APIResult<T> requestPost(String url, HttpHeaders requestHeaders, Object requestBody, Class<T> responseClass) {
		APIResult<T> result = new APIResult<>(responseClass.getSimpleName().replaceAll("(Response|response)$", ""));
		try {

			ByteArrayContent body = null;
			if(requestBody != null)
				body = ByteArrayContent.fromString("application/json;charset=UTF-8", JSONWriter.toJsonString(requestBody));

			HttpRequest httpRequest = requestFactory
					.buildPostRequest(new GenericUrl(url), body)
					.setParser(jsonFactory.createJsonObjectParser());

			if(requestHeaders != null) httpRequest.setHeaders(requestHeaders);

			HttpResponse response = httpRequest.execute();
			result.setResponseCode(response.getStatusCode());

			if(result.getResponseCode() != 200) {
				result.setError(Integer.toString(response.getStatusCode()), response.getStatusMessage());
			} else {
				ObjectMapper mapper = new ObjectMapper();
				mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
				T responseData = mapper.readValue(response.parseAsString(), responseClass);
				result.setData(responseData);

				if(responseData.isSuccess()) {
					result.setSuccess(true);
				} else {
					result.setError(String.valueOf(responseData.getCode()), responseData.getMessage());
				}
			}
		} catch(Exception e) {
			result.setException(e);
		}
		return result;
	}
}