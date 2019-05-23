package io.talken.dex.shared.service.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;

import java.util.Map;

public abstract class AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AbstractRestApiService.class);

	@FunctionalInterface
	private interface RequestSupplier {
		HttpRequest buildRequest() throws Exception;
	}

	private static HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
	private static JsonFactory jsonFactory = new JacksonFactory();

	protected <T extends CodeMessageResponseInterface> APIResult<T> requestPost(String url, Class<T> responseClass) {
		return requestPost(url, null, null, responseClass);
	}

	protected <T extends CodeMessageResponseInterface> APIResult<T> requestPost(String url, Object requestBody, Class<T> responseClass) {
		return requestPost(url, null, requestBody, responseClass);
	}

	protected <T extends CodeMessageResponseInterface> APIResult<T> requestPost(String url, HttpHeaders requestHeaders, Object requestBody, Class<T> responseClass) {
		return doRequest(() -> {
			ByteArrayContent body = null;
			if(requestBody != null)
				body = ByteArrayContent.fromString("application/json;charset=UTF-8", JSONWriter.toJsonString(requestBody));

			return requestFactory
					.buildPostRequest(new GenericUrl(url), body)
					.setParser(jsonFactory.createJsonObjectParser());
		}, requestHeaders, responseClass);
	}

	protected <T extends CodeMessageResponseInterface> APIResult<T> requestGet(String url, Class<T> responseClass) {
		return requestGet(url, null, null, responseClass);
	}

	protected <T extends CodeMessageResponseInterface> APIResult<T> requestGet(String url, Map<String, String> queryParams, Class<T> responseClass) {
		return requestGet(url, null, queryParams, responseClass);
	}

	protected <T extends CodeMessageResponseInterface> APIResult<T> requestGet(String url, HttpHeaders requestHeaders, Map<String, String> queryParams, Class<T> responseClass) {
		return doRequest(() -> {
			GenericUrl api = new GenericUrl(url);

			if(queryParams != null)
				api.putAll(queryParams);

			return requestFactory
					.buildGetRequest(api)
					.setParser(jsonFactory.createJsonObjectParser());
		}, requestHeaders, responseClass);
	}

	private <T extends CodeMessageResponseInterface> APIResult<T> doRequest(RequestSupplier httpRequestSupplier, HttpHeaders requestHeaders, Class<T> responseClass) {
		APIResult<T> result = new APIResult<>(responseClass.getSimpleName().replaceAll("(Response|response)$", ""));
		HttpResponse response = null;
		try {
			HttpRequest httpRequest = httpRequestSupplier.buildRequest();

			if(requestHeaders != null) httpRequest.setHeaders(requestHeaders);

			response = httpRequest.execute();

			result.setResponseCode(response.getStatusCode());

			if(!response.isSuccessStatusCode()) {
				result.setError(Integer.toString(response.getStatusCode()), response.getStatusMessage());
				logger.warn("{} API responses {} : {}", result.getApiName(), response.getStatusCode(), response.parseAsString());
			} else {
				ObjectMapper mapper = new ObjectMapper();
				mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
				T responseData = mapper.readValue(response.parseAsString(), responseClass);
				result.setData(responseData);

				if(responseData.isSuccess()) {
					result.setSuccess(true);
				} else {
					result.setError(responseData.getCode(), responseData.getMessage());
				}
			}
		} catch(HttpResponseException ex) {
			result.setResponseCode(ex.getStatusCode());
			result.setError(Integer.toString(ex.getStatusCode()), ex.getStatusMessage());
			logger.warn("{} API responses {} : {}", result.getApiName(), ex.getStatusCode(), ex.getContent());
		} catch(Exception e) {
			result.setException(e);
			logger.exception(e);
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
