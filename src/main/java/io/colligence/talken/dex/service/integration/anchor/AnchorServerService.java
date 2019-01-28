package io.colligence.talken.dex.service.integration.anchor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.service.integration.APIResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
public class AnchorServerService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorServerService.class);

	@Autowired
	private DexSettings dexSettings;

	private static HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
	private static JsonFactory jsonFactory = new JacksonFactory();

	private static String anchoringApiUrl;
	private static String deanchoringApiUrl;

	@PostConstruct
	private void init() {
		anchoringApiUrl = dexSettings.getServer().getAncAddress() + "/exchange/anchor/asset/anchor";
		deanchoringApiUrl = dexSettings.getServer().getAncAddress() + "/exchange/anchor/asset/deanchor";
	}

	public APIResult<AncServerAnchorResponse> requestAnchor(AncServerAnchorRequest request) {
		APIResult<AncServerAnchorResponse> result = new APIResult<>("Anchor");
		try {
			HttpResponse response = requestFactory
					.buildPostRequest(new GenericUrl(anchoringApiUrl), ByteArrayContent.fromString("application/json;charset=UTF-8", JSONWriter.toJsonString(request)))
					.setParser(jsonFactory.createJsonObjectParser())
					.execute();

			if(response.getStatusCode() != 200) {
				result.setResponseCode(Integer.toString(response.getStatusCode()));
				result.setError(Integer.toString(response.getStatusCode()), response.getStatusMessage());
			} else {
				ObjectMapper mapper = new ObjectMapper();
				mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
				AncServerAnchorResponse asar = mapper.readValue(response.parseAsString(), AncServerAnchorResponse.class);
				result.setData(asar);

				if(asar.getCode() == 200) {
					result.setSuccess(true);
				} else {
					result.setError(String.valueOf(asar.getCode()), asar.getDescription());
				}
			}
		} catch(Exception e) {
			result.setException(e);
		}
		return result;
	}

	public APIResult<AncServerDeanchorResponse> requestDeanchor(AncServerDeanchorRequest request) {
		APIResult<AncServerDeanchorResponse> result = new APIResult<>("Deanchor");
		try {
			HttpResponse response = requestFactory
					.buildPostRequest(new GenericUrl(deanchoringApiUrl), ByteArrayContent.fromString("application/json;charset=UTF-8", JSONWriter.toJsonString(request)))
					.setParser(jsonFactory.createJsonObjectParser())
					.execute();

			if(response.getStatusCode() != 200) {
				result.setResponseCode(Integer.toString(response.getStatusCode()));
				result.setError(Integer.toString(response.getStatusCode()), response.getStatusMessage());
			} else {
				ObjectMapper mapper = new ObjectMapper();
				mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
				AncServerDeanchorResponse asar = mapper.readValue(response.parseAsString(), AncServerDeanchorResponse.class);
				result.setData(asar);

				if(asar.getCode() == 200) {
					result.setSuccess(true);
				} else {
					result.setError(String.valueOf(asar.getCode()), asar.getDescription());
				}
			}
		} catch(Exception e) {
			result.setException(e);
		}
		return result;
	}
}
