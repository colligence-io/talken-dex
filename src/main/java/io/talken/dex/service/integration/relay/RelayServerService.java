package io.talken.dex.service.integration.relay;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Error;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.response.CustomTypeAdapter;
import com.apollographql.apollo.response.CustomTypeValue;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.DexSettings;
import io.talken.dex.api.service.DexTaskId;
import io.talken.dex.service.integration.APIResult;
import io.colligence.talken.graphql.relay.RelayAddContentsMutation;
import io.colligence.talken.graphql.relay.type.CustomType;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class RelayServerService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(RelayServerService.class);

	@Autowired
	private DexSettings dexSettings;

	private static ApolloClient apolloClient;
	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
	private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

	private final CustomTypeAdapter<LocalDateTime> customTypeAdapter = new CustomTypeAdapter<LocalDateTime>() {
		@Override
		public LocalDateTime decode(@NotNull CustomTypeValue value) {
			return LocalDateTime.parse(value.value.toString(), dtf);
		}

		@NotNull
		@Override
		public CustomTypeValue encode(@NotNull LocalDateTime value) {
			return new CustomTypeValue.GraphQLString(dtf.format(value));
		}
	};

	@PostConstruct
	private void init() {
		apolloClient = ApolloClient.builder()
				.serverUrl(dexSettings.getServer().getRlyAddress())
				.okHttpClient(okHttpClient)
				.addCustomTypeAdapter(CustomType.DATE, customTypeAdapter)
				.build();
	}

	public APIResult<RelayAddContentsResponse> requestAddContents(RelayMsgTypeEnum msgType, long userId, DexTaskId dexTaskId, RelayEncryptedContent<?> encData) {
		CompletableFuture<APIResult<RelayAddContentsResponse>> completableFuture = new CompletableFuture<>();

		HashMap<String, String> contents = new HashMap<>();
		contents.put("taskId", dexTaskId.getId());
		contents.put("data", encData.getEncrypted());

		apolloClient.mutate(
				RelayAddContentsMutation.builder()
						.msgType(msgType.getMsgType())
						.userId(Long.toString(userId))
						.msgContents(JSONWriter.toJsonStringSafe(contents))
						.pushTitle(msgType.name())
						.pushBody(msgType.name())
						.pushImage("")
						.build()
		).enqueue(new ApolloCall.Callback<RelayAddContentsMutation.Data>() {
			@Override
			public void onResponse(@NotNull Response<RelayAddContentsMutation.Data> response) {
				APIResult<RelayAddContentsResponse> apiResult = new APIResult<>("RelayAddContents");
				if(response.hasErrors()) {
					StringBuilder sb = new StringBuilder();
					for(Error error : response.errors()) sb.append(error.message()).append("\n");
					apiResult.setError("RelayError", sb.toString());
				} else {
					if(response.data() != null) {
						try {
							RelayAddContentsResponse result = new RelayAddContentsResponse();
							result.setTransId(response.data().addContents().transId());
							result.setStatus(response.data().addContents().status());
							result.setRegDt(response.data().addContents().regDt());
							result.setEndDt(response.data().addContents().endDt());
							apiResult.setSuccess(true);
							apiResult.setData(result);
						} catch(Exception ex) {
							apiResult.setException(ex);
						}
					} else {
						apiResult.setError("RelayError", "Relay server returned null.");
					}
				}

				completableFuture.complete(apiResult);
			}

			@Override
			public void onFailure(@NotNull ApolloException e) {
				APIResult<RelayAddContentsResponse> apiResult = new APIResult<>("RelayAddContents");
				apiResult.setException(e);
				completableFuture.complete(apiResult);
			}
		});


		APIResult<RelayAddContentsResponse> relayAddContentsResponseAPIResult;
		try {
			// FIXME : set timeout for get
			relayAddContentsResponseAPIResult = completableFuture.get();
		} catch(Exception e) {
			relayAddContentsResponseAPIResult = new APIResult<>("RelayAddContents");
			relayAddContentsResponseAPIResult.setException(e);
		}
		return relayAddContentsResponseAPIResult;
	}

}
