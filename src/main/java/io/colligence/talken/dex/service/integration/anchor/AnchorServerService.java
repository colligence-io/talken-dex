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
import io.colligence.talken.dex.exception.APICallException;
import io.colligence.talken.dex.service.integration.APIError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;

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

	public AncServerAnchorResponse requestAnchor(AncServerAnchorRequest request) throws APICallException, APIError {
		try {
			HttpResponse response = requestFactory
					.buildPostRequest(new GenericUrl(anchoringApiUrl), ByteArrayContent.fromString("application/json;charset=UTF-8", JSONWriter.toJsonString(request)))
					.setParser(jsonFactory.createJsonObjectParser())
					.execute();

			logger.logObjectAsJSON(request);

			// check http response is OK
			if(response.getStatusCode() != 200)
				throw new APICallException("Anchor", response.getStatusMessage());

			ObjectMapper mapper = new ObjectMapper();
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			AncServerAnchorResponse asar = mapper.readValue(response.parseAsString(), AncServerAnchorResponse.class);

			if(asar.getCode() != 200)
				throw new APIError("Anchor", String.valueOf(asar.getCode()), asar.getDescription(), asar);

			return asar;
		} catch(IOException e) {
			throw new APICallException(e, "Anchor");
		}
	}

	public AncServerDeanchorResponse requestDeanchor(AncServerDeanchorRequest request) throws APICallException, APIError {
		try {
			HttpResponse response = requestFactory
					.buildPostRequest(new GenericUrl(deanchoringApiUrl), ByteArrayContent.fromString("application/json;charset=UTF-8", JSONWriter.toJsonString(request)))
					.setParser(jsonFactory.createJsonObjectParser())
					.execute();

			// check http response is OK
			if(response.getStatusCode() != 200)
				throw new APICallException("Anchor", response.getStatusMessage());

			ObjectMapper mapper = new ObjectMapper();
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			AncServerDeanchorResponse asar = mapper.readValue(response.parseAsString(), AncServerDeanchorResponse.class);

			if(asar.getCode() != 200)
				throw new APIError("Anchor", String.valueOf(asar.getCode()), asar.getDescription(), asar);

			return asar;
		} catch(IOException e) {
			throw new APICallException(e, "Anchor");
		}
	}
}
