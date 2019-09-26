package io.talken.dex.shared.service.integration.wallet;

import com.google.api.client.http.HttpHeaders;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.talken.common.exception.common.IntegrationException;
import io.talken.common.persistence.jooq.tables.records.UserRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.common.util.integration.rest.RestApiClient;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.USER;

public class TalkenWalletService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TalkenWalletService.class);

	@Autowired
	private DSLContext dslContext;

	private final String apiUrl;

	public TalkenWalletService(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	public ObjectPair<Boolean, String> getAddress(long userId, String type, String symbol) throws IntegrationException {
		Optional<UserRecord> opt_user = dslContext.selectFrom(USER)
				.where(USER.ID.eq(userId).and(USER.WALLET_TOKEN_ACTIVATE_FLAG.eq(true)))
				.fetchOptional();

		if(!opt_user.isPresent()) return new ObjectPair<>(false, null);

		UserRecord userRecord = opt_user.get();

		String token = userRecord.getWalletToken();

		HttpHeaders headers = new HttpHeaders();
		headers.set("x-access-token", token);

		IntegrationResult<TalkenWalletRawResponse> wallets = RestApiClient.requestGet(apiUrl + "/api/v1/wallet", headers, null, TalkenWalletRawResponse.class);

		if(wallets.isSuccess()) {
			String jsonString = wallets.getData().getData();

			JsonElement jsonElement = new JsonParser().parse(jsonString);

			if(jsonElement.isJsonArray()) {
				try {
					TalkenWalletListResponse walletsData = new Gson().fromJson(jsonString, TalkenWalletListResponse.class);

					Optional<String> address = walletsData.stream()
							.filter((_w) -> {
								return _w.getType() != null && _w.getType().equalsIgnoreCase(type);
							})
							.filter((_w) -> {
								return _w.getSymbol() != null && _w.getSymbol().equalsIgnoreCase(symbol);
							})
							.map(_w -> { return _w.getAddress();})
							.filter(_a -> _a != null)
							.findAny();

					return new ObjectPair<>(true, address.orElse(null));
				} catch(Exception ex) {
					// FIXME : failed parse json, assume it's error
					logger.exception(ex);
					return new ObjectPair<>(false, null);
				}

			} else {
				try {
					TalkenWalletResponse walletResponse = new Gson().fromJson(jsonString, TalkenWalletResponse.class);

					if(walletResponse == null || walletResponse.getCode().equals("WALLET_NOT_FOUND")) {
						logger.warn("Cannot process wallet response : {}", jsonString);
					}
				} catch(Exception ex) {
					logger.exception(ex);
				}
				return new ObjectPair<>(false, null);
			}
		} else {
			throw new IntegrationException(wallets);
		}
	}
}
