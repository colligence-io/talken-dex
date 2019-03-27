package io.talken.dex.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.talken.common.persistence.enums.LangTypeEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.pojos.TokenExchangeRate;
import io.talken.common.persistence.jooq.tables.records.*;
import io.talken.common.persistence.redis.RedisConsts;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.DoubleKeyObject;
import io.talken.common.util.collection.DoubleKeyTable;
import io.talken.common.util.collection.SingleKeyTable;
import io.talken.dex.api.dto.UpdateHolderResult;
import io.talken.dex.exception.*;
import io.talken.dex.service.integration.signer.SignServerService;
import io.talken.dex.service.integration.stellar.StellarNetworkService;
import io.talken.dex.util.StellarConverter;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import javax.annotation.PostConstruct;
import java.util.*;

import static io.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
@DependsOn("dbmigration")
public class TokenMetaService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaService.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private SignServerService signServerService;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	private static Long lastTradeAggregationUpdatedTimestamp = null;
	private static Long lastExchangeRateUpdatedTimestamp = null;
	private static Long loadTimestamp;

	private SingleKeyTable<String, TokenMetaData> tmTable = new SingleKeyTable<>();
	private SingleKeyTable<String, TokenMetaData> miTable = new SingleKeyTable<>();

	// checked trustline registry
	private DoubleKeyTable<String, Asset, TrustedAsset> checkedTrusts = new DoubleKeyTable<>();

	private static class TrustedAsset implements DoubleKeyObject<String, Asset> {
		private String accountID;
		private Asset trustFor;

		private TrustedAsset(String accountID, Asset trustFor) {
			this.accountID = accountID;
			this.trustFor = trustFor;
		}

		@Override
		public String __getMKey__() {
			return accountID;
		}

		@Override
		public Asset __getSKey__() {
			return trustFor;
		}
	}

	@PostConstruct
	private void init() throws TokenMetaLoadException {
		load();
		lastExchangeRateUpdatedTimestamp = loadTimestamp;
		lastTradeAggregationUpdatedTimestamp = loadTimestamp;
	}

	public Map<String, TokenMetaData> forceReload() throws TokenMetaLoadException {
		load();
		return tmTable.__getRawData();
	}

	public void checkTaExrAndUpdate() throws TokenMetaLoadException {
		boolean reloaded = false;
		Long redisTaDataUpdated = null;
		Object taval = redisTemplate.opsForValue().get(RedisConsts.KEY_ASSET_OHLCV_UPDATED);
		if(taval != null) {
			redisTaDataUpdated = Long.valueOf(taval.toString());
			if(lastTradeAggregationUpdatedTimestamp < redisTaDataUpdated) {
				load();
				reloaded = true;
				lastTradeAggregationUpdatedTimestamp = redisTaDataUpdated;
			}
		}

		Long redisExrDataUpdated = null;
		Object exrval = redisTemplate.opsForValue().get(RedisConsts.KEY_ASSET_EXRATE_UPDATED);
		if(exrval != null) {
			redisExrDataUpdated = Long.valueOf(exrval.toString());
			if(lastExchangeRateUpdatedTimestamp < redisExrDataUpdated) {
				if(!reloaded) load();
				lastExchangeRateUpdatedTimestamp = redisExrDataUpdated;
			}
		}
	}

	private void load() throws TokenMetaLoadException {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

			SingleKeyTable<String, TokenMetaData> newTmTable = new SingleKeyTable<>();
			SingleKeyTable<String, TokenMetaData> newMiTable = new SingleKeyTable<>();

			// preload token_meta_id / marketpairList map
			Map<Long, List<TokenMetaData.MarketPairInfo>> _tmpMap = new HashMap<>();
			for(TokenManagedMarketPairRecord _tmp : dslContext.selectFrom(TOKEN_MANAGED_MARKET_PAIR).fetch()) {
				Long metaId = _tmp.getTokenMetaId();
				if(!_tmpMap.containsKey(metaId))
					_tmpMap.put(metaId, new ArrayList<>());
				_tmpMap.get(metaId).add(_tmp.into(TokenMetaData.MarketPairInfo.class));
			}

			// preload token_meta_id / managedHolderList map
			Map<Long, List<TokenMetaData.HolderAccountInfo>> _tmhMap = new HashMap<>();
			for(TokenManagedHolderRecord _tmh : dslContext.selectFrom(TOKEN_MANAGED_HOLDER).fetch()) {
				Long metaId = _tmh.getTokenMetaId();
				if(!_tmhMap.containsKey(metaId))
					_tmhMap.put(metaId, new ArrayList<>());
				_tmhMap.get(metaId).add(_tmh.into(TokenMetaData.HolderAccountInfo.class));
			}

			// preload token_meta_id / managedInfo map
			Map<Long, TokenMetaData.ManagedInfo> _miMap = new HashMap<>();
			for(TokenManagedInfoRecord _tmi : dslContext.selectFrom(TOKEN_MANAGED_INFO).fetch()) {
				_miMap.put(_tmi.getTokenMetaId(), _tmi.into(TokenMetaData.ManagedInfo.class));
			}

			// preload token_meta_id / token_exchange_rate map
			Map<Long, List<TokenExchangeRate>> _erMap = new HashMap<>();
			for(TokenExchangeRateRecord _er : dslContext.selectFrom(TOKEN_EXCHANGE_RATE).fetch()) {
				Long metaId = _er.getTokenMetaId();
				if(!_erMap.containsKey(metaId))
					_erMap.put(metaId, new ArrayList<>());
				_erMap.get(metaId).add(_er.into(TokenExchangeRate.class));
			}

			// preload token_meta_id / token_info map
			Map<Long, Map<LangTypeEnum, TokenMetaData.EntryInfo>> _teMap = new HashMap<>();
			for(TokenEntryRecord _te : dslContext.selectFrom(TOKEN_ENTRY).fetch()) {
				Long metaId = _te.getTokenMetaId();
				if(!_teMap.containsKey(metaId))
					_teMap.put(metaId, new HashMap<>());
				_teMap.get(metaId).put(_te.getLangcode(), _te.into(TokenMetaData.EntryInfo.class));
			}

			// preload token_meta_id / token_aux list map
			Map<Long, Map<TokenMetaAuxCodeEnum, Object>> _auxMap = new HashMap<>();
			for(TokenMetaAuxRecord _aux : dslContext.selectFrom(TOKEN_META_AUX).fetch()) {
				Long metaId = _aux.getTokenMetaId();
				if(!_auxMap.containsKey(metaId))
					_auxMap.put(metaId, new HashMap<>());

				Object data;
				switch(_aux.getAuxCode().getDataType()) {
					case DOUBLE:
						data = _aux.getDataD();
						break;
					case STRING:
						data = _aux.getDataS();
						break;
					case INT:
						data = _aux.getDataI();
						break;
					case LONG:
						data = _aux.getDataL();
						break;
					case TEXT:
						data = _aux.getDataT();
						break;
					case JSON:
						data = mapper.readValue(_aux.getDataJData(), Class.forName(_aux.getDataJClass()));
						break;
					default:
						throw new IllegalArgumentException("Broken TokenMetaAux data");
				}
				_auxMap.get(metaId).put(_aux.getAuxCode(), data);
			}

			// preload token_meta_id / token meta data map
			Map<Long, TokenMetaData> tmIdMap = new HashMap<>();
			for(TokenMetaRecord _tmr : dslContext.selectFrom(TOKEN_META).fetch()) {
				tmIdMap.put(_tmr.getId(), _tmr.into(TokenMetaData.class));
			}

			// Composite TokenMetaData
			for(TokenMetaData _tm : tmIdMap.values()) {
				Long metaId = _tm.getId();

				if(_auxMap.containsKey(metaId)) {
					_tm.setAux(_auxMap.get(metaId));
				}

				if(_teMap.containsKey(metaId)) {
					_tm.setEntryInfo(_teMap.get(metaId));
				}

				// build name map
				Map<LangTypeEnum, String> nameMap = new HashMap<>();
				for(LangTypeEnum _lt : LangTypeEnum.values()) {
					if(_tm.getEntryInfo() != null && _tm.getEntryInfo().containsKey(_lt))
						nameMap.put(_lt, _tm.getEntryInfo().get(_lt).getName());
					else nameMap.put(_lt, _tm.getNamekey());
				}
				_tm.setName(nameMap);

				// build exchange rate
				if(_erMap.containsKey(metaId)) {
					Map<String, Double> exr = new HashMap<>();
					for(TokenExchangeRate _er : _erMap.get(metaId))
						exr.put(_er.getCountertype(), _er.getPrice());
					_tm.setExchangeRate(exr);
				}

				if(_miMap.containsKey(metaId)) {
					_tm.setManagedInfo(_miMap.get(metaId));
					newMiTable.insert(_tm);
				}

				newTmTable.insert(_tm);
			}

			// Composite managed info
			for(TokenMetaData _tmd : newMiTable.__getRawData().values()) {
				logger.info("Load managed accounts for {}({})", _tmd.getNamekey(), _tmd.getSymbol());
				TokenMetaData.ManagedInfo mi = _tmd.getManagedInfo();
				mi.setAssetHolderAccounts(_tmhMap.get(_tmd.getId()));
				mi.setAssetCode(_tmd.getSymbol());
				mi.setAssetIssuer(KeyPair.fromAccountId(mi.getIssueraddress()));
				mi.setAssetType(new AssetTypeCreditAlphaNum4(_tmd.getSymbol(), mi.getAssetIssuer()));
				mi.setAssetBase(KeyPair.fromAccountId(mi.getBaseaddress()));
				mi.setDeanchorFeeHolder(KeyPair.fromAccountId(mi.getDeancfeeholderaddress()));
				mi.setOfferFeeHolder(KeyPair.fromAccountId(mi.getOfferfeeholderaddress()));

				mi.setMarketPair(new HashMap<>());
				for(TokenMetaData.MarketPairInfo _mp : _tmpMap.get(_tmd.getId())) {
					mi.getMarketPair().put(tmIdMap.get(_mp.getCounterMetaId()).getSymbol(), _mp);
				}
			}

			if(!verifyManaged(newMiTable)) {
				logger.error("Cannot verify token meta data");

				// TODO : this is commented out for partial integration with signServer
				// after all accounts integrated with signServer, this must be enabled instead of just logging

				//throw new TokenMetaLoadException("Cannot verify token meta data");
			}

			tmTable = newTmTable;
			miTable = newMiTable;
			loadTimestamp = UTCUtil.getNowTimestamp_s();
		} catch(Exception ex) {
			throw new TokenMetaLoadException(ex);
		}
	}

	private boolean verifyManaged(SingleKeyTable<String, TokenMetaData> checkTarget) {
		boolean trustFailed = false;
		for(TokenMetaData _tm : checkTarget.__getRawData().values()) {
			if(!checkTrust(_tm.getManagedInfo().getAssetBase(), _tm.getManagedInfo())) trustFailed = true;
			if(!checkTrust(_tm.getManagedInfo().getOfferFeeHolder(), _tm.getManagedInfo())) trustFailed = true;
			if(!checkTrust(_tm.getManagedInfo().getDeanchorFeeHolder(), _tm.getManagedInfo())) trustFailed = true;
		}
		return !trustFailed;
	}

	private boolean checkTrust(KeyPair source, TokenMetaData.ManagedInfo target) {
		TrustedAsset ta = new TrustedAsset(source.getAccountId(), target.getAssetType());
		if(checkedTrusts.has(ta)) return true;

		boolean trusted = false;
		try {
			Server server = stellarNetworkService.pickServer();

			AccountResponse sourceAccount = server.accounts().account(KeyPair.fromAccountId(ta.accountID));

			for(AccountResponse.Balance _bal : sourceAccount.getBalances()) {
				if(ta.trustFor.equals(_bal.getAsset())) trusted = true;
			}

			if(!trusted) {
				logger.info("No trust on {} for {} / {}", source.getAccountId(), target.getAssetCode(), target.getAssetIssuer().getAccountId());
				Transaction tx = stellarNetworkService.getTransactionBuilderFor(sourceAccount)
						.addOperation(
								new ChangeTrustOperation.Builder(
										target.getAssetType(),
										String.valueOf(StellarConverter.rawToDoubleString(Long.MAX_VALUE))
								).build()
						)
						.build();

				signServerService.signTransaction(tx);

				SubmitTransactionResponse txResponse = server.submitTransaction(tx);

				if(txResponse.isSuccess()) {
					logger.info("Trustline made on {} for {} / {}", source.getAccountId(), target.getAssetCode(), target.getAssetIssuer().getAccountId());
					trusted = true;
				} else {
					SubmitTransactionResponse.Extras.ResultCodes resultCodes = txResponse.getExtras().getResultCodes();
					StringJoiner sj = new StringJoiner(",");
					if(resultCodes.getOperationsResultCodes() != null) resultCodes.getOperationsResultCodes().forEach(sj::add);
					logger.error("Cannot make trustline on {} for {} / {} : {} - {}", source.getAccountId(), target.getAssetCode(), target.getAssetIssuer().getAccountId(), resultCodes.getTransactionResultCode(), sj.toString());
				}
			}
		} catch(Exception ex) {
			logger.exception(ex, "Trust Check Exception : {}", source.getAccountId());
		}

		if(trusted) {
			checkedTrusts.insert(ta);
			logger.info("Trustline on {} for {} / {} verified", source.getAccountId(), target.getAssetCode(), target.getAssetIssuer().getAccountId());
			return true;
		} else {
			return false;
		}
	}

	public TokenMetaData getTokenMeta(String symbol) throws TokenMetaDataNotFoundException {
		if(!tmTable.has(symbol.toUpperCase())) throw new TokenMetaDataNotFoundException(symbol);
		return tmTable.select(symbol.toUpperCase());
	}

	public Map<String, TokenMetaData> getTokenMetaList() {
		return tmTable.__getRawData();
	}

	public Map<String, TokenMetaData> getManagedInfoList() {
		return miTable.__getRawData();
	}

	private TokenMetaData.ManagedInfo getPack(String assetCode) throws TokenMetaDataNotFoundException {
		return Optional.ofNullable(
				Optional.ofNullable(
						miTable.select(assetCode)
				).orElseThrow(() -> new TokenMetaDataNotFoundException(assetCode))
						.getManagedInfo())
				.orElseThrow(() -> new TokenMetaDataNotFoundException(assetCode));
	}

	public Asset getAssetType(String code) throws TokenMetaDataNotFoundException {
		return getPack(code).getAssetType();
	}

	public KeyPair getOfferFeeHolderAccount(String code) throws TokenMetaDataNotFoundException {
		return getPack(code).getOfferFeeHolder();
	}

	public KeyPair getDeanchorFeeHolderAccount(String code) throws TokenMetaDataNotFoundException {
		return getPack(code).getDeanchorFeeHolder();
	}

	public KeyPair getBaseAccount(String code) throws TokenMetaDataNotFoundException {
		return getPack(code).getAssetBase();
	}

	public String getActiveHolderAccountAddress(String code) throws TokenMetaDataNotFoundException, ActiveAssetHolderAccountNotFoundException {
		Optional<TokenMetaData.HolderAccountInfo> opt_aha = getPack(code).getAssetHolderAccounts().stream()
				.filter(TokenMetaData.HolderAccountInfo::getActiveFlag)
				.findAny();
		if(opt_aha.isPresent()) return opt_aha.get().getAddress();
		else {
			logger.warn("There is no active asset holder account for {}, use random hot account.", code);

			Optional<TokenMetaData.HolderAccountInfo> opt_ahh = getPack(code).getAssetHolderAccounts().stream()
					.filter(TokenMetaData.HolderAccountInfo::getHotFlag)
					.findAny();
			if(opt_ahh.isPresent()) return opt_ahh.get().getAddress();
			else throw new ActiveAssetHolderAccountNotFoundException(code);
		}
	}

	public UpdateHolderResult updateHolder(String assetCode, String address, Boolean isHot, Boolean isActive) throws TokenMetaDataNotFoundException, AccountNotFoundException, UpdateHolderStatusException {

		TokenMetaData.ManagedInfo pack = getPack(assetCode);
		List<TokenMetaData.HolderAccountInfo> holders = pack.getAssetHolderAccounts();

		Optional<TokenMetaData.HolderAccountInfo> opt_ah = holders.stream().filter(_ah -> _ah.getAddress().equals(address)).findAny();
		if(!opt_ah.isPresent()) throw new AccountNotFoundException("Holder", address);

		TokenMetaData.HolderAccountInfo holder = opt_ah.get();

		if(isHot != null) {
			if(!isHot) {
				// check this update will remove last hot account
				if(holder.getHotFlag() && holders.stream().filter(TokenMetaData.HolderAccountInfo::getHotFlag).count() == 1)
					throw new UpdateHolderStatusException(assetCode, address, "last hot account cannot be freezed.");

				dslContext.update(TOKEN_MANAGED_HOLDER)
						.set(TOKEN_MANAGED_HOLDER.HOT_FLAG, false)
						.where(TOKEN_MANAGED_HOLDER.TOKEN_META_ID.eq(pack.getTokenMetaId()).and(TOKEN_MANAGED_HOLDER.ADDRESS.eq(address)))
						.execute();
				holder.setHotFlag(false);
			} else {
				dslContext.update(TOKEN_MANAGED_HOLDER)
						.set(TOKEN_MANAGED_HOLDER.HOT_FLAG, true)
						.where(TOKEN_MANAGED_HOLDER.TOKEN_META_ID.eq(pack.getTokenMetaId()).and(TOKEN_MANAGED_HOLDER.ADDRESS.eq(address)))
						.execute();
				holder.setHotFlag(true);
			}

		}

		if(isActive != null) {
			if(!isActive) {
				// check this update will remove last active account
				if(holder.getActiveFlag() && holders.stream().filter(TokenMetaData.HolderAccountInfo::getActiveFlag).count() == 1)
					throw new UpdateHolderStatusException(assetCode, address, "last active account cannot be deactivated.");

				dslContext.update(TOKEN_MANAGED_HOLDER)
						.set(TOKEN_MANAGED_HOLDER.ACTIVE_FLAG, false)
						.where(TOKEN_MANAGED_HOLDER.TOKEN_META_ID.eq(pack.getTokenMetaId()).and(TOKEN_MANAGED_HOLDER.ADDRESS.eq(address)))
						.execute();
				holder.setActiveFlag(false);
			} else {
				dslContext.update(TOKEN_MANAGED_HOLDER)
						.set(TOKEN_MANAGED_HOLDER.ACTIVE_FLAG, true)
						.where(TOKEN_MANAGED_HOLDER.TOKEN_META_ID.eq(pack.getTokenMetaId()).and(TOKEN_MANAGED_HOLDER.ADDRESS.eq(address)))
						.execute();
				holder.setActiveFlag(true);
			}
		}

		UpdateHolderResult result = new UpdateHolderResult();
		result.setManagedAccountPack(pack);
		return result;
	}
}
