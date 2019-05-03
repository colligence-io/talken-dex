package io.talken.dex.governance.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.RegionEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.pojos.TokenMetaExrate;
import io.talken.common.persistence.jooq.tables.records.*;
import io.talken.common.persistence.redis.AssetExchangeRate;
import io.talken.common.persistence.redis.AssetOHLCData;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.DoubleKeyObject;
import io.talken.common.util.collection.DoubleKeyTable;
import io.talken.common.util.collection.SingleKeyTable;
import io.talken.dex.governance.service.integration.signer.SignServerService;
import io.talken.dex.shared.StellarConverter;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.exception.TokenMetaLoadException;
import io.talken.dex.shared.service.StellarNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
public class TokenMetaGovService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaGovService.class);

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

	private Map<Long, TokenMeta> tmIdMap = new HashMap<>();
	private SingleKeyTable<String, TokenMeta> tmTable = new SingleKeyTable<>();
	private SingleKeyTable<String, TokenMeta> miTable = new SingleKeyTable<>();

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

	@Scheduled(fixedDelay = 5000)
	public void checkTaExrAndUpdate() throws TokenMetaLoadException {
		boolean reloaded = false;
		Long redisTaDataUpdated = null;
		Object taval = redisTemplate.opsForValue().get(AssetOHLCData.REDIS_UPDATED_KEY);
		if(taval != null) {
			redisTaDataUpdated = Long.valueOf(taval.toString());
			if(lastTradeAggregationUpdatedTimestamp < redisTaDataUpdated) {
				load();
				reloaded = true;
				lastTradeAggregationUpdatedTimestamp = redisTaDataUpdated;
			}
		}

		Long redisExrDataUpdated = null;
		Object exrval = redisTemplate.opsForValue().get(AssetExchangeRate.REDIS_UPDATED_KEY);
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
			logger.info("Build TokenMetaData");
			ObjectMapper mapper = new ObjectMapper();
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

			SingleKeyTable<String, TokenMeta> newTmTable = new SingleKeyTable<>();
			SingleKeyTable<String, TokenMeta> newMiTable = new SingleKeyTable<>();

			// preload token_meta_id / marketpairList map
			Map<Long, List<TokenMeta.MarketPairInfo>> _tmpMap = new HashMap<>();
			for(TokenMetaManagedMarketpairRecord _tmp : dslContext.selectFrom(TOKEN_META_MANAGED_MARKETPAIR).fetch()) {
				Long metaId = _tmp.getTmId();
				if(!_tmpMap.containsKey(metaId))
					_tmpMap.put(metaId, new ArrayList<>());
				_tmpMap.get(metaId).add(_tmp.into(TokenMeta.MarketPairInfo.class));
			}

			// preload token_meta_id / managedHolderList map
			Map<Long, List<TokenMeta.HolderAccountInfo>> _tmhMap = new HashMap<>();
			for(TokenMetaManagedHolderRecord _tmh : dslContext.selectFrom(TOKEN_META_MANAGED_HOLDER).fetch()) {
				Long metaId = _tmh.getTmId();
				if(!_tmhMap.containsKey(metaId))
					_tmhMap.put(metaId, new ArrayList<>());

				TokenMeta.HolderAccountInfo _tmhData = _tmh.into(TokenMeta.HolderAccountInfo.class);
				_tmhData.setAddress(_tmhData.getAddress().trim());

				_tmhMap.get(metaId).add(_tmhData);
			}

			// preload token_meta_id / managedInfo map
			Map<Long, TokenMeta.ManagedInfo> _miMap = new HashMap<>();
			for(TokenMetaManagedRecord _tmi : dslContext.selectFrom(TOKEN_META_MANAGED).fetch()) {

				TokenMeta.ManagedInfo _miData = _tmi.into(TokenMeta.ManagedInfo.class);
				_miData.setIssueraddress(_miData.getIssueraddress().trim());
				_miData.setBaseaddress(_miData.getBaseaddress());
				_miData.setOfferfeeholderaddress(_miData.getOfferfeeholderaddress().trim());
				_miData.setDeancfeeholderaddress(_miData.getDeancfeeholderaddress().trim());

				_miMap.put(_tmi.getTmId(), _miData);
			}

			// preload token_meta_id / token_exchange_rate map
			Map<Long, List<TokenMetaExrate>> _erMap = new HashMap<>();
			for(TokenMetaExrateRecord _er : dslContext.selectFrom(TOKEN_META_EXRATE).fetch()) {
				Long metaId = _er.getTmId();
				if(!_erMap.containsKey(metaId))
					_erMap.put(metaId, new ArrayList<>());
				_erMap.get(metaId).add(_er.into(TokenMetaExrate.class));
			}

			// preload token_meta_id / token_info map
			Map<Long, Map<RegionEnum, TokenMeta.EntryInfo>> _teMap = new HashMap<>();
			for(TokenEntryRecord _te : dslContext.selectFrom(TOKEN_ENTRY).fetch()) {
				Long metaId = _te.getTmId();
				if(!_teMap.containsKey(metaId))
					_teMap.put(metaId, new HashMap<>());
				_teMap.get(metaId).put(_te.getRegion(), _te.into(TokenMeta.EntryInfo.class));
			}

			// preload token_meta_id / token_aux list map
			Map<Long, Map<TokenMetaAuxCodeEnum, Object>> _auxMap = new HashMap<>();
			for(TokenMetaAuxRecord _aux : dslContext.selectFrom(TOKEN_META_AUX).fetch()) {
				Long metaId = _aux.getTmId();
				if(!_auxMap.containsKey(metaId))
					_auxMap.put(metaId, new HashMap<>());

				Object data;
				switch(_aux.getAuxCode().getDataType()) {
					case DOUBLE:
						data = _aux.getDataD();
						break;
					case STRING:
						data = (_aux.getDataS() != null) ? _aux.getDataS().trim() : "";
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
			Map<Long, TokenMeta> newTmIdMap = new HashMap<>();
			for(TokenMetaRecord _tmr : dslContext.selectFrom(TOKEN_META).fetch()) {
				newTmIdMap.put(_tmr.getId(), _tmr.into(TokenMeta.class));
			}

			// Composite TokenMeta
			for(TokenMeta _tm : newTmIdMap.values()) {
				Long metaId = _tm.getId();

				if(_auxMap.containsKey(metaId)) {
					_tm.setAux(_auxMap.get(metaId));
				}

				if(_teMap.containsKey(metaId)) {
					_tm.setEntryInfo(_teMap.get(metaId));
				}

				// build name map
				Map<RegionEnum, String> nameMap = new HashMap<>();
				for(RegionEnum _lt : RegionEnum.values()) {
					if(_tm.getEntryInfo() != null && _tm.getEntryInfo().containsKey(_lt))
						nameMap.put(_lt, _tm.getEntryInfo().get(_lt).getName());
					else nameMap.put(_lt, _tm.getNamekey());
				}
				_tm.setName(nameMap);

				// build exchange rate
				if(_erMap.containsKey(metaId)) {
					Map<String, Double> exr = new HashMap<>();
					for(TokenMetaExrate _er : _erMap.get(metaId))
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
			for(TokenMeta _tmd : newMiTable.__getRawData().values()) {
				logger.info("Load managed accounts for {}({})", _tmd.getNamekey(), _tmd.getSymbol());
				TokenMeta.ManagedInfo mi = _tmd.getManagedInfo();
				mi.setAssetHolderAccounts(_tmhMap.get(_tmd.getId()));
				mi.setAssetCode(_tmd.getSymbol());
				mi.setAssetIssuer(KeyPair.fromAccountId(mi.getIssueraddress()));
				mi.setAssetType(new AssetTypeCreditAlphaNum4(_tmd.getSymbol(), mi.getAssetIssuer()));
				mi.setAssetBase(KeyPair.fromAccountId(mi.getBaseaddress()));
				mi.setDeanchorFeeHolder(KeyPair.fromAccountId(mi.getDeancfeeholderaddress()));
				mi.setOfferFeeHolder(KeyPair.fromAccountId(mi.getOfferfeeholderaddress()));

				mi.setMarketPair(new HashMap<>());
				for(TokenMeta.MarketPairInfo _mp : _tmpMap.get(_tmd.getId())) {
					mi.getMarketPair().put(newTmIdMap.get(_mp.getTmIdCounter()).getSymbol(), _mp);
				}
			}

			if(!verifyManaged(newMiTable)) {
				logger.error("Cannot verify token meta data");

				// TODO : this is commented out for partial integration with signServer
				// after all accounts integrated with signServer, this must be enabled instead of just logging

				//throw new TokenMetaLoadException("Cannot verify token meta data");
			}

			tmIdMap = newTmIdMap;
			tmTable = newTmTable;
			miTable = newMiTable;
			loadTimestamp = UTCUtil.getNowTimestamp_s();
			exportToRedis();

		} catch(Exception ex) {
			throw new TokenMetaLoadException(ex);
		}
	}

	// deep copy
	private void exportToRedis() {
		TokenMetaTable data = new TokenMetaTable();
		data.setUpdated(loadTimestamp);

		tmTable.forEach(_tm -> {
			TokenMetaTable.Meta meta = data.forMeta(_tm.__getSKey__());
			meta.setId(_tm.getId());
			meta.setNameKey(_tm.getNamekey());
			meta.setSymbol(_tm.getSymbol());
			meta.setPlatform(_tm.getPlatform());
			meta.setNativeFlag(_tm.getNativeFlag());
			meta.setIconUrl(_tm.getIconUrl());
			meta.setThumbnailUrl(_tm.getThumbnailUrl());
			meta.setViewUnitExpn(_tm.getViewUnitExpn());
			meta.setCmcId(_tm.getCmcId());
			meta.setName(_tm.getName());

			if(_tm.getEntryInfo() != null) {
				_tm.getEntryInfo().forEach((_eiKey, _ei) -> {
					TokenMetaTable.EntryInfo entryInfo = meta.forEntry(_eiKey);
					entryInfo.setId(_ei.getId());
					entryInfo.setName(_ei.getName());
					entryInfo.setRevision(_ei.getRevision());
					entryInfo.setNumFollower(_ei.getNumFollower());
					entryInfo.setShowFlag(_ei.getShowFlag());
					entryInfo.setConfirmFlag(_ei.getConfirmFlag());
					if(_ei.getUpdateTimestamp() == null)
						entryInfo.setUpdateTimestamp(UTCUtil.toTimestamp_s(_ei.getCreateTimestamp()));
					else entryInfo.setUpdateTimestamp(UTCUtil.toTimestamp_s(_ei.getUpdateTimestamp()));
				});
			}

			if(_tm.getManagedInfo() != null) {
				TokenMeta.ManagedInfo _mi = _tm.getManagedInfo();

				TokenMetaTable.ManagedInfo mi = new TokenMetaTable.ManagedInfo();
				mi.setAssetCode(_mi.getAssetCode());
				mi.setIssuerAddress(_mi.getIssueraddress());
				mi.setBaseAddress(_mi.getBaseaddress());
				mi.setOfferFeeHolderAddress(_mi.getOfferfeeholderaddress());
				mi.setDeancFeeHolderAddress(_mi.getDeancfeeholderaddress());
				mi.setUpdateTimestamp(UTCUtil.toTimestamp_s(_mi.getUpdateTimestamp()));

				if(_mi.getMarketPair() != null) {
					_mi.getMarketPair().forEach((_mpKey, _mp) -> {
						TokenMetaTable.MarketPairInfo mp = mi.forMarketPair(_mpKey);
						mp.setActiveFlag(_mp.getActiveFlag());
						mp.setTradeUnitExpn(_mp.getTradeUnitExpn());
						if(_mp.getAggrTimestamp() != null) mp.setAggrTimestamp(UTCUtil.toTimestamp_s(_mp.getAggrTimestamp()));
						mp.setTradeCount(_mp.getTradeCount());
						mp.setBaseVolume(_mp.getBaseVolume());
						mp.setCounterVolume(_mp.getCounterVolume());
						mp.setPriceAvg(_mp.getPriceAvg());
						mp.setPriceH(_mp.getPriceH());
						mp.setPriceL(_mp.getPriceL());
						mp.setPriceO(_mp.getPriceO());
						mp.setPriceC(_mp.getPriceC());
						if(_mp.getUpdateTimestamp() == null)
							mp.setUpdateTimestamp(UTCUtil.toTimestamp_s(_mp.getCreateTimestamp()));
						else mp.setUpdateTimestamp(UTCUtil.toTimestamp_s(_mp.getUpdateTimestamp()));
					});
				}

				if(_mi.getAssetHolderAccounts() != null) {
					for(TokenMeta.HolderAccountInfo _hai : _mi.getAssetHolderAccounts()) {
						TokenMetaTable.HolderAccountInfo hai = mi.newHolderAccountInfo();
						hai.setAddress(_hai.getAddress());
						hai.setActiveFlag(_hai.getActiveFlag());
						hai.setHotFlag(_hai.getHotFlag());
					}
				}

				meta.setManagedInfo(mi);
			}

			meta.setAux(_tm.getAux());
			meta.setExchangeRate(_tm.getExchangeRate());
			if(_tm.getUpdateTimestamp() == null) meta.setUpdateTimestamp(UTCUtil.toTimestamp_s(_tm.getCreateTimestamp()));
			else meta.setUpdateTimestamp(UTCUtil.toTimestamp_s(_tm.getUpdateTimestamp()));
		});

		redisTemplate.opsForValue().set(TokenMetaTable.REDIS_KEY, data);
		redisTemplate.opsForValue().set(TokenMetaTable.REDIS_UDPATED_KEY, loadTimestamp);
	}


	private boolean verifyManaged(SingleKeyTable<String, TokenMeta> checkTarget) {
		boolean trustFailed = false;
		for(TokenMeta _tm : checkTarget.__getRawData().values()) {
			if(!checkTrust(_tm.getManagedInfo().getAssetBase(), _tm.getManagedInfo())) trustFailed = true;
			if(!checkTrust(_tm.getManagedInfo().getOfferFeeHolder(), _tm.getManagedInfo())) trustFailed = true;
			if(!checkTrust(_tm.getManagedInfo().getDeanchorFeeHolder(), _tm.getManagedInfo())) trustFailed = true;
		}
		return !trustFailed;
	}

	private boolean checkTrust(KeyPair source, TokenMeta.ManagedInfo target) {
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
										String.valueOf(StellarConverter.rawToActualString(Long.MAX_VALUE))
								).build()
						)
						.build();

				signServerService.signStellarTransaction(tx);

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

	public TokenMeta getMeta(String assetCode) throws TokenMetaNotFoundException {
		return Optional.ofNullable(
				tmTable.select(assetCode)
		).orElseThrow(() -> new TokenMetaNotFoundException(assetCode));
	}

	public TokenMeta.ManagedInfo getManaged(String assetCode) throws TokenMetaNotFoundException {
		return Optional.ofNullable(
				miTable.select(assetCode).getManagedInfo()
		).orElseThrow(() -> new TokenMetaNotFoundException(assetCode));
	}

	public TokenMeta getTokenMetaById(Long tm_id) throws TokenMetaNotFoundException {
		return Optional.ofNullable(tmIdMap.get(tm_id)).orElseThrow(() -> new TokenMetaNotFoundException(Long.toString(tm_id)));
	}
//
//	public KeyPair getOfferFeeHolderAccount(String code) throws TokenMetaNotFoundException {
//		return getManaged(code).getOfferFeeHolder();
//	}
//
//	public KeyPair getDeanchorFeeHolderAccount(String code) throws TokenMetaNotFoundException {
//		return getManaged(code).getDeanchorFeeHolder();
//	}
//
//	public KeyPair getBaseAccount(String code) throws TokenMetaNotFoundException {
//		return getManaged(code).getAssetBase();
//	}
//
//	public String getActiveHolderAccountAddress(String code) throws TokenMetaNotFoundException, ActiveAssetHolderAccountNotFoundException {
//		Optional<TokenMeta.HolderAccountInfo> opt_aha = getManaged(code).getAssetHolderAccounts().stream()
//				.filter(TokenMeta.HolderAccountInfo::getActiveFlag)
//				.findAny();
//		if (opt_aha.isPresent()) return opt_aha.get().getAddress();
//		else {
//			logger.warn("There is no active asset holder account for {}, use random hot account.", code);
//
//			Optional<TokenMeta.HolderAccountInfo> opt_ahh = getManaged(code).getAssetHolderAccounts().stream()
//					.filter(TokenMeta.HolderAccountInfo::getHotFlag)
//					.findAny();
//			if (opt_ahh.isPresent()) return opt_ahh.get().getAddress();
//			else throw new ActiveAssetHolderAccountNotFoundException(code);
//		}
//	}
//
//	public UpdateHolderResult updateHolder(String assetCode, String address, Boolean isHot, Boolean isActive) throws TokenMetaNotFoundException, AccountNotFoundException, UpdateHolderStatusException {
//
//		TokenMeta.ManagedInfo pack = getManaged(assetCode);
//		List<TokenMeta.HolderAccountInfo> holders = pack.getAssetHolderAccounts();
//
//		Optional<TokenMeta.HolderAccountInfo> opt_ah = holders.stream().filter(_ah -> _ah.getAddress().equals(address)).findAny();
//		if(!opt_ah.isPresent()) throw new AccountNotFoundException("Holder", address);
//
//		TokenMeta.HolderAccountInfo holder = opt_ah.get();
//
//		if(isHot != null) {
//			if(!isHot) {
//				// check this update will remove last hot account
//				if(holder.getHotFlag() && holders.stream().filter(TokenMeta.HolderAccountInfo::getHotFlag).count() == 1)
//					throw new UpdateHolderStatusException(assetCode, address, "last hot account cannot be freezed.");
//
//				dslContext.update(TOKEN_MANAGED_HOLDER)
//						.set(TOKEN_MANAGED_HOLDER.HOT_FLAG, false)
//						.where(TOKEN_MANAGED_HOLDER.TOKEN_META_ID.eq(pack.getTokenMetaId()).and(TOKEN_MANAGED_HOLDER.ADDRESS.eq(address)))
//						.execute();
//				holder.setHotFlag(false);
//			} else {
//				dslContext.update(TOKEN_MANAGED_HOLDER)
//						.set(TOKEN_MANAGED_HOLDER.HOT_FLAG, true)
//						.where(TOKEN_MANAGED_HOLDER.TOKEN_META_ID.eq(pack.getTokenMetaId()).and(TOKEN_MANAGED_HOLDER.ADDRESS.eq(address)))
//						.execute();
//				holder.setHotFlag(true);
//			}
//
//		}
//
//		if(isActive != null) {
//			if(!isActive) {
//				// check this update will remove last active account
//				if(holder.getActiveFlag() && holders.stream().filter(TokenMeta.HolderAccountInfo::getActiveFlag).count() == 1)
//					throw new UpdateHolderStatusException(assetCode, address, "last active account cannot be deactivated.");
//
//				dslContext.update(TOKEN_MANAGED_HOLDER)
//						.set(TOKEN_MANAGED_HOLDER.ACTIVE_FLAG, false)
//						.where(TOKEN_MANAGED_HOLDER.TOKEN_META_ID.eq(pack.getTokenMetaId()).and(TOKEN_MANAGED_HOLDER.ADDRESS.eq(address)))
//						.execute();
//				holder.setActiveFlag(false);
//			} else {
//				dslContext.update(TOKEN_MANAGED_HOLDER)
//						.set(TOKEN_MANAGED_HOLDER.ACTIVE_FLAG, true)
//						.where(TOKEN_MANAGED_HOLDER.TOKEN_META_ID.eq(pack.getTokenMetaId()).and(TOKEN_MANAGED_HOLDER.ADDRESS.eq(address)))
//						.execute();
//				holder.setActiveFlag(true);
//			}
//		}
//
//		UpdateHolderResult result = new UpdateHolderResult();
//		result.setManagedAccountPack(pack);
//		return result;
//	}
}
