package io.talken.dex.api.service.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import io.talken.common.util.JSONWriter;

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
		HttpResponse response = null;
		try {

			ByteArrayContent body = null;
			if(requestBody != null)
				body = ByteArrayContent.fromString("application/json;charset=UTF-8", JSONWriter.toJsonString(requestBody));

			HttpRequest httpRequest = requestFactory
					.buildPostRequest(new GenericUrl(url), body)
					.setParser(jsonFactory.createJsonObjectParser());

			if(requestHeaders != null) httpRequest.setHeaders(requestHeaders);

			response = httpRequest.execute();

			result.setResponseCode(response.getStatusCode());

			if(!response.isSuccessStatusCode()) {
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
		} catch(HttpResponseException ex) {
			result.setResponseCode(ex.getStatusCode());
			result.setError(Integer.toString(ex.getStatusCode()), ex.getStatusMessage());
		} catch(Exception e) {
			result.setException(e);
		} finally {
			try {
				if(response != null) {
					// ensure set response code even on exception
					result.setResponseCode(response.getStatusCode());
					response.disconnect();
				}
			} catch(Exception ignore) {}
		}

		return result;
	}
}
