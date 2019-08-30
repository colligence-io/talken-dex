package io.talken.dex.shared.service.integration.wallet;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpHeaders;
import com.google.gson.JsonParser;
import io.talken.common.exception.common.IntegrationException;
import io.talken.common.persistence.jooq.tables.records.UserRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.common.util.integration.rest.RestApiClient;
import io.talken.common.util.integration.slack.AdminAlarmService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.USER;

public class TalkenWalletService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TalkenWalletService.class);

	@Autowired
	private AdminAlarmService adminAlarmService;

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

			if(new JsonParser().parse(jsonString).isJsonArray()) {
				try {
					ObjectMapper mapper = new ObjectMapper();
					mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
					TalkenWalletListResponse walletsData = mapper.readValue(jsonString, TalkenWalletListResponse.class);

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
					adminAlarmService.exception(logger, ex);
					return new ObjectPair<>(false, null);
				}

			} else {
				// FIXME : not jsonarray, assume it's error
				return new ObjectPair<>(false, null);
			}
		} else {
			throw new IntegrationException(wallets);
		}
	}
}
