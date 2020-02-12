package io.talken.dex.governance.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.talken.common.persistence.enums.RegionEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.pojos.*;
import io.talken.common.persistence.jooq.tables.records.*;
import io.talken.common.persistence.redis.AssetExchangeRate;
import io.talken.common.persistence.redis.AssetOHLCData;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.DoubleKeyObject;
import io.talken.common.util.collection.DoubleKeyTable;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TokenMetaTableService;
import io.talken.dex.shared.exception.TokenMetaLoadException;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.requests.ErrorResponse;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
@DependsOn("dbmigration")
public class TokenMetaGovService extends TokenMetaTableService {
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


			// preload token_meta_id / marketpairList map
			Map<Long, List<TokenMetaManagedMarketpair>> _tmMpMap = new HashMap<>();
			for(TokenMetaManagedMarketpairRecord _tmp : dslContext.selectFrom(TOKEN_META_MANAGED_MARKETPAIR).fetch()) {
				Long metaId = _tmp.getTmId();
				if(!_tmMpMap.containsKey(metaId))
					_tmMpMap.put(metaId, new ArrayList<>());
				_tmMpMap.get(metaId).add(_tmp.into(TokenMetaManagedMarketpair.class));
			}

			// preload token_meta_id / managedHolderList map
			Map<Long, List<TokenMetaManagedHolder>> _tmHaMap = new HashMap<>();
			for(TokenMetaManagedHolderRecord _tmh : dslContext.selectFrom(TOKEN_META_MANAGED_HOLDER).fetch()) {
				Long metaId = _tmh.getTmId();
				if(!_tmHaMap.containsKey(metaId))
					_tmHaMap.put(metaId, new ArrayList<>());

				TokenMetaManagedHolder _tmhData = _tmh.into(TokenMetaManagedHolder.class);
				_tmhData.setAddress(_tmhData.getAddress().trim());

				_tmHaMap.get(metaId).add(_tmhData);
			}

			// preload token_meta_id / managedInfo map
			Map<Long, TokenMetaManaged> _tmMiMap = new HashMap<>();
			for(TokenMetaManagedRecord _tmi : dslContext.selectFrom(TOKEN_META_MANAGED).fetch()) {

				// trim data for sure
				TokenMetaManaged _miData = _tmi.into(TokenMetaManaged.class);
				_miData.setIssueraddress(_miData.getIssueraddress().trim());
				_miData.setOfferfeeholderaddress(_miData.getOfferfeeholderaddress().trim());
				_miData.setDeancfeeholderaddress(_miData.getDeancfeeholderaddress().trim());
				_miData.setSwapfeeholderaddress(_miData.getSwapfeeholderaddress().trim());
				if(_miData.getDistributoraddress() != null)
					_miData.setDistributoraddress(_miData.getDistributoraddress().trim());

				_tmMiMap.put(_tmi.getTmId(), _miData);
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
			Map<Long, Map<RegionEnum, TokenEntry>> _tmEiMap = new HashMap<>();
			for(TokenEntryRecord _te : dslContext.selectFrom(TOKEN_ENTRY).fetch()) {
				Long metaId = _te.getTmId();
				if(!_tmEiMap.containsKey(metaId))
					_tmEiMap.put(metaId, new HashMap<>());
				_tmEiMap.get(metaId).put(_te.getRegion(), _te.into(TokenEntry.class));
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

			// preload token_meta_platform
			Map<String, TokenMetaPlatform> _tmpMap = new HashMap<>();
			for(TokenMetaPlatformRecord _tmp : dslContext.selectFrom(TOKEN_META_PLATFORM).fetch()) {
				_tmpMap.put(_tmp.getPlatform(), _tmp.into(TokenMetaPlatform.class));
			}

			// preload token_meta_id / token meta data map
			Map<Long, TokenMeta> newTmIdMap = new HashMap<>();
			for(TokenMetaRecord _tmr : dslContext.selectFrom(TOKEN_META).fetch()) {
				newTmIdMap.put(_tmr.getId(), _tmr.into(TokenMeta.class));
			}

			// build new meta table
			TokenMetaTable newTmTable = new TokenMetaTable();

			// Composite TokenMeta
			for(TokenMeta _tm : newTmIdMap.values()) {
				Long metaId = _tm.getId();
				String symbol = _tm.getSymbol().toUpperCase();

				TokenMetaTable.Meta meta = newTmTable.forMeta(symbol);

				meta.setId(_tm.getId());
				meta.setNameKey(_tm.getNamekey());
				meta.setSymbol(_tm.getSymbol());
				meta.setPlatform(_tm.getPlatform());
				meta.setIconUrl(_tm.getIconUrl());
				meta.setThumbnailUrl(_tm.getThumbnailUrl());
				meta.setViewUnitExpn(_tm.getViewUnitExpn());
				meta.setUnitDecimals(_tm.getUnitDecimals());
				meta.setCmcId(_tm.getCmcId());

				if(_tm.getPlatform() != null && _tmpMap.containsKey(_tm.getPlatform())) {
					meta.setNativeFlag(_tmpMap.get(_tm.getPlatform()).getNativeFlag());
					meta.setBctxType(_tmpMap.get(_tm.getPlatform()).getBctxType());
				}

				if(_auxMap.containsKey(metaId)) {
					meta.setAux(_auxMap.get(metaId));
				}

				if(_tmEiMap.containsKey(metaId)) {
					for(Map.Entry<RegionEnum, TokenEntry> _eikv : _tmEiMap.get(metaId).entrySet()) {
						TokenMetaTable.EntryInfo entryInfo = meta.forEntry(_eikv.getKey());
						entryInfo.setId(_eikv.getValue().getId());
						entryInfo.setName(_eikv.getValue().getName());
						entryInfo.setRevision(_eikv.getValue().getRevision());
						entryInfo.setNumFollower(_eikv.getValue().getNumFollower());
						entryInfo.setShowFlag(_eikv.getValue().getShowFlag());
						entryInfo.setConfirmFlag(_eikv.getValue().getConfirmFlag());
						if(_eikv.getValue().getUpdateTimestamp() == null)
							entryInfo.setUpdateTimestamp(UTCUtil.toTimestamp_s(_eikv.getValue().getCreateTimestamp()));
						else entryInfo.setUpdateTimestamp(UTCUtil.toTimestamp_s(_eikv.getValue().getUpdateTimestamp()));
					}
				}

				// build name map
				Map<RegionEnum, String> nameMap = new HashMap<>();
				for(RegionEnum _lt : RegionEnum.values()) {
					if(meta.getEntryInfo() != null && meta.getEntryInfo().containsKey(_lt))
						nameMap.put(_lt, meta.getEntryInfo().get(_lt).getName());
					else nameMap.put(_lt, _tm.getNamekey());
				}
				meta.setName(nameMap);

				// build exchange rate
				if(_erMap.containsKey(metaId)) {
					Map<String, BigDecimal> exr = new HashMap<>();
					for(TokenMetaExrate _er : _erMap.get(metaId))
						exr.put(_er.getCountertype(), _er.getPrice());
					meta.setExchangeRate(exr);
				}

				// managed info
				if(_tmMiMap.containsKey(metaId)) {
					TokenMetaManaged _mi = _tmMiMap.get(metaId);
					TokenMetaTable.ManagedInfo mi = new TokenMetaTable.ManagedInfo();

					// basic managed info data
					mi.setAssetCode(meta.getSymbol());
					mi.setIssuerAddress(_mi.getIssueraddress());
					mi.setOfferFeeHolderAddress(_mi.getOfferfeeholderaddress());
					mi.setDeancFeeHolderAddress(_mi.getDeancfeeholderaddress());
					mi.setSwapFeeHolderAddress(_mi.getSwapfeeholderaddress());
					mi.setDistributorAddress(_mi.getDistributoraddress());
					if(_mi.getUpdateTimestamp() == null)
						mi.setUpdateTimestamp(UTCUtil.toTimestamp_s(_mi.getCreateTimestamp()));
					else
						mi.setUpdateTimestamp(UTCUtil.toTimestamp_s(_mi.getUpdateTimestamp()));

					mi.prepareCache();

					// holder accounts
					if(_tmHaMap.containsKey(metaId)) {
						for(TokenMetaManagedHolder _hai : _tmHaMap.get(metaId)) {
							TokenMetaTable.HolderAccountInfo hai = mi.newHolderAccountInfo();
							hai.setAddress(_hai.getAddress());
							hai.setActiveFlag(_hai.getActiveFlag());
							hai.setHotFlag(_hai.getHotFlag());
						}
					}

					// market pairs
					if(_tmMpMap.containsKey(metaId)) {
						for(TokenMetaManagedMarketpair _mp : _tmMpMap.get(metaId)) {
							TokenMetaTable.MarketPairInfo mp = mi.forMarketPair(newTmIdMap.get(_mp.getTmIdCounter()).getSymbol());

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
						}
					}

					meta.setManagedInfo(mi);
				}

				if(_tm.getUpdateTimestamp() == null) meta.setUpdateTimestamp(UTCUtil.toTimestamp_s(_tm.getCreateTimestamp()));
				else meta.setUpdateTimestamp(UTCUtil.toTimestamp_s(_tm.getUpdateTimestamp()));
			}

			if(!verifyManaged(newTmTable)) {
				logger.error("Cannot verify token meta data");
				throw new TokenMetaLoadException("Cannot verify token meta data");
			}

			loadTimestamp = UTCUtil.getNowTimestamp_s();
			updateStorage(newTmTable);

			redisTemplate.opsForValue().set(TokenMetaTable.REDIS_KEY, newTmTable);
			redisTemplate.opsForValue().set(TokenMetaTable.REDIS_UDPATED_KEY, loadTimestamp);
		} catch(Exception ex) {
			throw new TokenMetaLoadException(ex);
		}
	}

	private boolean verifyManaged(TokenMetaTable metaTable) {
		boolean trustFailed = false;
		for(TokenMetaTable.Meta _tm : metaTable.values()) {
			if(_tm.isManaged()) {
				if(!checkTrust(_tm.getManagedInfo().dexOfferFeeHolderAccount(), _tm.getManagedInfo())) trustFailed = true;
				if(!checkTrust(_tm.getManagedInfo().dexDeanchorFeeHolderAccount(), _tm.getManagedInfo())) trustFailed = true;
				if(!checkTrust(_tm.getManagedInfo().dexSwapFeeHolderAccount(), _tm.getManagedInfo())) trustFailed = true;
			}
		}
		return !trustFailed;
	}

	private boolean checkTrust(KeyPair source, TokenMetaTable.ManagedInfo target) {
		TrustedAsset ta = new TrustedAsset(source.getAccountId(), target.dexAssetType());
		if(checkedTrusts.has(ta)) return true;

		boolean trusted = false;
		try {
			Server server = stellarNetworkService.pickServer();

			AccountResponse sourceAccount = server.accounts().account(KeyPair.fromAccountId(ta.accountID).getAccountId());

			for(AccountResponse.Balance _bal : sourceAccount.getBalances()) {
				if(ta.trustFor.equals(_bal.getAsset())) trusted = true;
			}

			if(!trusted) {
				logger.info("No trust on {} for {} / {}", source.getAccountId(), target.getAssetCode(), target.dexIssuerAccount().getAccountId());
				Transaction tx = new Transaction.Builder(sourceAccount, stellarNetworkService.getNetwork())
						.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
						.setOperationFee(stellarNetworkService.getNetworkFee())
						.addOperation(
								new ChangeTrustOperation.Builder(
										target.dexAssetType(),
										String.valueOf(StellarConverter.rawToActualString(BigInteger.valueOf(Long.MAX_VALUE)))
								).build()
						)
						.build();

				signServerService.signStellarTransaction(tx);

				SubmitTransactionResponse txResponse = server.submitTransaction(tx);

				if(txResponse.isSuccess()) {
					logger.info("Trustline made on {} for {} / {}", source.getAccountId(), target.getAssetCode(), target.dexIssuerAccount().getAccountId());
					trusted = true;
				} else {
					ObjectPair<String, String> resultCodesFromExtra = StellarConverter.getResultCodesFromExtra(txResponse);
					logger.error("Cannot make trustline on {} for {} / {} : {} - {}", source.getAccountId(), target.getAssetCode(), target.dexIssuerAccount().getAccountId(), resultCodesFromExtra.first(), resultCodesFromExtra.second());
				}
			}
		} catch(ErrorResponse er) {
			try {
				JsonObject json = JsonParser.parseString(er.getBody()).getAsJsonObject();
				logger.error("Trustline Check Error {} : {} {}", source.getAccountId(), json.get("status").getAsString(), json.get("title").getAsString());
			} catch(Exception ex) {
				logger.error("Trustline Check Error {} : {} {}", source.getAccountId(), er.getBody(), er.getMessage());
			}
		} catch(Exception ex) {
			logger.exception(ex, "Trust Check Exception : {}", source.getAccountId());
		}

		if(trusted) {
			checkedTrusts.insert(ta);
			logger.info("Trustline on {} for {} / {} verified", source.getAccountId(), target.getAssetCode(), target.dexIssuerAccount().getAccountId());
			return true;
		} else {
			return false;
		}
	}


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
