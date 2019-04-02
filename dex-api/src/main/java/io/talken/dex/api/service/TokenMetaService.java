package io.talken.dex.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.exception.ActiveAssetHolderAccountNotFoundException;
import io.talken.dex.shared.exception.TokenMetaDataNotFoundException;
import io.talken.dex.shared.exception.TokenMetaLoadException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Service
@Scope("singleton")
public class TokenMetaService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaService.class);

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	private static Long loadTimestamp;

	private TokenMetaTable tmTable = new TokenMetaTable();
	private TokenMetaTable miTable = new TokenMetaTable();

	@PostConstruct
	private void init() throws TokenMetaLoadException {
		checkAndReload();
	}

	public void checkAndReload() throws TokenMetaLoadException {
		try {
			boolean reload = false;
			if(tmTable == null || loadTimestamp == null) reload = true;
			else {
				Object tmval = redisTemplate.opsForValue().get(TokenMetaTable.REDIS_UDPATED_KEY);
				if(tmval != null) {
					Long redisTmUpdated = Long.valueOf(tmval.toString());
					if(loadTimestamp < redisTmUpdated) {
						reload = true;
					}
				}
			}

			if(reload) {
				ObjectMapper mapper = new ObjectMapper();
				mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

				TokenMetaTable newTmTable = (TokenMetaTable) redisTemplate.opsForValue().get(TokenMetaTable.REDIS_KEY);
				TokenMetaTable newMiTable = new TokenMetaTable();

				newMiTable.setUpdated(newTmTable.getUpdated());
				newTmTable.entrySet().stream()
						.filter(_kv -> _kv.getValue().isManaged())
						.peek(_kv -> _kv.getValue().getManagedInfo().prepareCache())
						.forEach(_kv -> newMiTable.put(_kv.getKey(), _kv.getValue()));

				tmTable = newTmTable;
				miTable = newMiTable;
				loadTimestamp = UTCUtil.getNowTimestamp_s();

				logger.info("Token Meta loaded : all {}, managed {}, timestamp {}", tmTable.size(), miTable.size(), loadTimestamp);
			}
		} catch(Exception ex) {
			throw new TokenMetaLoadException(ex);
		}
	}

	public TokenMetaTable.Meta getTokenMeta(String symbol) throws TokenMetaDataNotFoundException {
		if(!tmTable.containsKey(symbol.toUpperCase())) throw new TokenMetaDataNotFoundException(symbol);
		return tmTable.get(symbol.toUpperCase());
	}

	public TokenMetaTable getTokenMetaList() {
		return tmTable;
	}

	public TokenMetaTable getManagedInfoList() {
		return miTable;
	}

	private TokenMetaTable.ManagedInfo getPack(String assetCode) throws TokenMetaDataNotFoundException {
		return Optional.ofNullable(
				Optional.ofNullable(
						miTable.get(assetCode)
				).orElseThrow(() -> new TokenMetaDataNotFoundException(assetCode))
						.getManagedInfo())
				.orElseThrow(() -> new TokenMetaDataNotFoundException(assetCode));
	}

	public Asset getAssetType(String code) throws TokenMetaDataNotFoundException {
		return getPack(code).getCache().getAssetType();
	}

	public KeyPair getOfferFeeHolderAccount(String code) throws TokenMetaDataNotFoundException {
		return getPack(code).getCache().getOfferFeeHolder();
	}

	public KeyPair getDeanchorFeeHolderAccount(String code) throws TokenMetaDataNotFoundException {
		return getPack(code).getCache().getDeanchorFeeHolder();
	}

	public KeyPair getBaseAccount(String code) throws TokenMetaDataNotFoundException {
		return getPack(code).getCache().getAssetBase();
	}

	public String getActiveHolderAccountAddress(String code) throws TokenMetaDataNotFoundException, ActiveAssetHolderAccountNotFoundException {
		Optional<TokenMetaTable.HolderAccountInfo> opt_aha = getPack(code).getAssetHolderAccounts().stream()
				.filter(TokenMetaTable.HolderAccountInfo::getActiveFlag)
				.findAny();
		if(opt_aha.isPresent()) return opt_aha.get().getAddress();
		else {
			logger.warn("There is no active asset holder account for {}, use random hot account.", code);

			Optional<TokenMetaTable.HolderAccountInfo> opt_ahh = getPack(code).getAssetHolderAccounts().stream()
					.filter(TokenMetaTable.HolderAccountInfo::getHotFlag)
					.findAny();
			if(opt_ahh.isPresent()) return opt_ahh.get().getAddress();
			else throw new ActiveAssetHolderAccountNotFoundException(code);
		}
	}
}
