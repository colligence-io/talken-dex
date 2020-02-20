package io.talken.dex.api.service.integration.relay;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Error;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.response.CustomTypeAdapter;
import com.apollographql.apollo.response.CustomTypeValue;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.dex.api.ApiSettings;
import io.talken.dex.api.graphql.relay.RelayAddContentsMutation;
import io.talken.dex.api.graphql.relay.type.CustomType;
import io.talken.dex.api.service.TokenMetaService;
import io.talken.dex.api.service.integration.relay.dto.RelayTransferDTO;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.exception.BlockChainPlatformNotSupportedException;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Deprecated
@Service
public class RelayServerService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(RelayServerService.class);

	@Autowired
	private ApiSettings apiSettings;

	@Autowired
	private TokenMetaService tmService;

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
				.serverUrl(apiSettings.getIntegration().getRelay().getApiUrl())
				.okHttpClient(okHttpClient)
				.addCustomTypeAdapter(CustomType.DATE, customTypeAdapter)
				.build();
	}

	public IntegrationResult<RelayAddContentsResponse> requestAddContents(RelayMsgTypeEnum msgType, long userId, DexTaskId dexTaskId, RelayEncryptedContent<?> encData) {
		CompletableFuture<IntegrationResult<RelayAddContentsResponse>> completableFuture = new CompletableFuture<>();

		HashMap<String, String> contents = new HashMap<>();
		contents.put("taskId", dexTaskId.getId());
		contents.put("data", encData.getEncrypted());

		for(Map.Entry<String, String> _dkv : encData.getDescription().entrySet()) {
			contents.put("d_" + _dkv.getKey(), _dkv.getValue());
		}

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
				IntegrationResult<RelayAddContentsResponse> apiResult = new IntegrationResult<>("RelayAddContents");
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
				IntegrationResult<RelayAddContentsResponse> apiResult = new IntegrationResult<>("RelayAddContents");
				apiResult.setException(e);
				completableFuture.complete(apiResult);
			}
		});


		IntegrationResult<RelayAddContentsResponse> relayAddContentsResponseAPIResult;
		try {
			// FIXME : set timeout for get
			relayAddContentsResponseAPIResult = completableFuture.get();
		} catch(Exception e) {
			relayAddContentsResponseAPIResult = new IntegrationResult<>("RelayAddContents");
			relayAddContentsResponseAPIResult.setException(e);
		}
		return relayAddContentsResponseAPIResult;
	}


	public RelayTransferDTO createTransferDTObase(String assetCode) throws TokenMetaNotFoundException, BlockChainPlatformNotSupportedException {
		TokenMetaTable.Meta tokenMeta = tmService.getTokenMeta(assetCode);

		String platform_name = tokenMeta.getPlatform();
		if(platform_name == null) throw new BlockChainPlatformNotSupportedException(assetCode);

		BlockChainPlatformEnum platform;
		try {
			platform = BlockChainPlatformEnum.valueOf(platform_name);
		} catch(IllegalArgumentException ex) {
			throw new BlockChainPlatformNotSupportedException(assetCode);
		}

		RelayTransferDTO dto = new RelayTransferDTO();
		dto.setPlatform(platform);
		dto.setWalletType(platform.getWalletType());
		dto.setSignType(platform.getWalletType().getSignType());
		dto.setSymbol(assetCode);
		if(tokenMeta.getAux() != null) {
			for(Map.Entry<TokenMetaAuxCodeEnum, Object> auxEntry : tokenMeta.getAux().entrySet()) {
				if(auxEntry.getKey().equals(TokenMetaAuxCodeEnum.TOKEN_CARD_THEME_COLOR))
					dto.getAux().put(auxEntry.getKey().name(), auxEntry.getValue());
			}
		}
		return dto;
	}
}
