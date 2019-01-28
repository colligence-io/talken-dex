package io.colligence.talken.dex.service.integration.txTunnel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import io.colligence.talken.common.persistence.enums.BlockChainPlatformEnum;
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
public class TransactionTunnelService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TransactionTunnelService.class);

	@Autowired
	private DexSettings dexSettings;

	private static HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
	private static JsonFactory jsonFactory = new JacksonFactory();

	private static String formatString;

	@PostConstruct
	private void init() {
		formatString = dexSettings.getServer().getTxtAddress() + "/v1/%s/tx/broadcast";
	}

	public APIResult<TxtServerResponse> requestTxTunnel(BlockChainPlatformEnum platform, TxtServerRequest request) {
		APIResult<TxtServerResponse> result = new APIResult<>("TxTunnel");

		try {
			String url = String.format(formatString, platform.getPlatformTxType());

			HttpResponse response = requestFactory
					.buildPostRequest(new GenericUrl(url), ByteArrayContent.fromString("application/json;charset=UTF-8", JSONWriter.toJsonString(request)))
					.setParser(jsonFactory.createJsonObjectParser())
					.execute();

			if(response.getStatusCode() != 200) {
				result.setResponseCode(Integer.toString(response.getStatusCode()));
				result.setError(Integer.toString(response.getStatusCode()), response.getStatusMessage());
			} else {
				ObjectMapper mapper = new ObjectMapper();
				mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
				TxtServerResponse txtr = mapper.readValue(response.parseAsString(), TxtServerResponse.class);
				result.setData(txtr);

				if(txtr.isSuccess()) {
					result.setSuccess(true);
				} else {
					result.setError(String.valueOf(txtr.getCode()), txtr.getMessage());
				}
			}
		} catch(Exception e) {
			result.setException(e);
		}

		return result;
	}
}
