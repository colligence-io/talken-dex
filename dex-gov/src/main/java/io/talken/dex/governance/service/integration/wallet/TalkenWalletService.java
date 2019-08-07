package io.talken.dex.governance.service.integration.wallet;

import com.google.api.client.http.HttpHeaders;
import io.talken.common.exception.common.RestApiErrorException;
import io.talken.common.persistence.jooq.tables.records.UserRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.AbstractRestApiService;
import io.talken.common.util.integration.RestApiResult;
import io.talken.dex.governance.GovSettings;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.USER;

@Service
@Scope("singleton")
public class TalkenWalletService extends AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TalkenWalletService.class);

	@Autowired
	private GovSettings govSettings;

	@Autowired
	private DSLContext dslContext;

	private String apiUrl;

	@PostConstruct
	private void init() {
		this.apiUrl = govSettings.getIntegration().getWallet().getApiUrl();
	}

	public ObjectPair<Boolean, String> getAddress(long userId, String type, String symbol) throws RestApiErrorException {
		Optional<UserRecord> opt_user = dslContext.selectFrom(USER)
				.where(USER.ID.eq(userId).and(USER.WALLET_TOKEN_ACTIVATE_FLAG.eq(true)))
				.fetchOptional();

		if(!opt_user.isPresent()) return new ObjectPair<>(false, null);

		UserRecord userRecord = opt_user.get();

		String token = userRecord.getWalletToken();

		HttpHeaders headers = new HttpHeaders();
		headers.set("x-access-token", token);

		RestApiResult<TalkenWalletListResponse> wallets = requestGet(apiUrl + "/api/v1/wallet", headers, null, TalkenWalletListResponse.class);

		if(wallets.isSuccess()) {
			Optional<String> address = wallets.getData().stream()
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
		} else {
			throw new RestApiErrorException(wallets);
		}
	}
}
