package io.colligence.talken.dex.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.colligence.talken.common.CommonConsts;
import io.colligence.talken.common.RunningProfile;
import io.colligence.talken.common.persistence.enums.LangTypeEnum;
import io.colligence.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.colligence.talken.common.persistence.jooq.tables.pojos.TokenExchangeRate;
import io.colligence.talken.common.persistence.jooq.tables.pojos.TokenInfo;
import io.colligence.talken.common.persistence.jooq.tables.records.*;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.UTCUtil;
import io.colligence.talken.common.util.collection.SingleKeyTable;
import io.colligence.talken.dex.api.dto.UpdateHolderResult;
import io.colligence.talken.dex.exception.*;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeCreditAlphaNum4;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Server;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

import static io.colligence.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
public class TokenMetaService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaService.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	private static Long loadTimestamp;

	private SingleKeyTable<String, TokenMetaData> tmTable = new SingleKeyTable<>();
	private SingleKeyTable<String, TokenMetaData> miTable = new SingleKeyTable<>();

	private HashSet<String> checkedAccounts = new HashSet<>();

	@PostConstruct
	private void init() throws InternalServerErrorException {
		load();
	}

	public Map<String, TokenMetaData> forceReload() throws InternalServerErrorException {
		load();
		return tmTable.__getRawData();
	}

	public void checkUpdateAndReload() throws InternalServerErrorException {
		Long redisTaDataUpdated = null;
		try {
			Object val = redisTemplate.opsForValue().get(CommonConsts.REDIS.KEY_ASSET_OHLCV_UPDATED);
			if(val != null)
				redisTaDataUpdated = Long.valueOf(val.toString());
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get data {} from redis", CommonConsts.REDIS.KEY_ASSET_OHLCV_UPDATED);
			throw new InternalServerErrorException(ex);
		}

		if(redisTaDataUpdated != null) {
			if(loadTimestamp == null || loadTimestamp < redisTaDataUpdated) {
				load();
				loadTimestamp = redisTaDataUpdated;
				return;
			}
		}

		Long redisExrDataUpdated = null;
		try {
			Object val = redisTemplate.opsForValue().get(CommonConsts.REDIS.KEY_ASSET_EXRATE_UPDATED);
			if(val != null)
				redisExrDataUpdated = Long.valueOf(val.toString());
		} catch(
				Exception ex) {
			logger.exception(ex, "Cannot get data {} from redis", CommonConsts.REDIS.KEY_ASSET_EXRATE_UPDATED);
			throw new InternalServerErrorException(ex);
		}

		if(redisExrDataUpdated != null) {
			if(loadTimestamp == null || loadTimestamp < redisExrDataUpdated) {
				load();
				loadTimestamp = redisExrDataUpdated;
			}
		}
	}

	private void load() throws InternalServerErrorException {
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

			// preload token_entry_id / tokenInfo map
			Map<Long, TokenInfo> _tiMap = new HashMap<>();
			for(TokenInfoRecord _ti : dslContext.selectFrom(TOKEN_INFO).fetch()) {
				_tiMap.put(_ti.getTokenId(), _ti.into(TokenInfo.class));
			}

			// preload token_meta_id / token_info map
			Map<Long, Map<LangTypeEnum, TokenMetaData.EntryInfo>> _teMap = new HashMap<>();
			for(TokenEntryRecord _te : dslContext.selectFrom(TOKEN_ENTRY).fetch()) {
				Long metaId = _te.getTokenMetaId();
				if(!_teMap.containsKey(metaId))
					_teMap.put(metaId, new HashMap<>());
				TokenMetaData.EntryInfo ei = _te.into(TokenMetaData.EntryInfo.class);
				ei.setInfo(_tiMap.get(_te.getId()));
				_teMap.get(metaId).put(_te.getLangcode(), ei);
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
				TokenMetaData tmd = _tmr.into(TokenMetaData.class);
				if(tmd.getRefUrls() != null && !tmd.getRefUrls().isEmpty()) {
					tmd.setUrls(mapper.readValue(tmd.getRefUrls(), TokenMetaUrlData.class));
				}
				tmIdMap.put(_tmr.getId(), tmd);
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
				mi.setAssetIssuer(getKeyPair(mi.getIssueraddress()));
				mi.setAssetType(new AssetTypeCreditAlphaNum4(_tmd.getSymbol(), mi.getAssetIssuer()));
				mi.setAssetBase(getKeyPair(mi.getBaseaddress()));
				mi.setDeanchorFeeHolder(getKeyPair(mi.getDeancfeeholderaddress()));
				mi.setOfferFeeHolder(getKeyPair(mi.getOfferfeeholderaddress()));

				mi.setMarketPair(new HashMap<>());
				for(TokenMetaData.MarketPairInfo _mp : _tmpMap.get(_tmd.getId())) {
					mi.getMarketPair().put(tmIdMap.get(_mp.getCounterMetaId()).getSymbol(), _mp);
				}
			}

			tmTable = newTmTable;
			miTable = newMiTable;
			loadTimestamp = UTCUtil.getNowTimestamp_s();
		} catch(Exception ex) {
			throw new InternalServerErrorException(ex);
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

	private KeyPair getKeyPair(String accountID) throws AccountNotFoundException {
		// First, check to make sure that the destination account exists.
		// You could skip this, but if the account does not exist, you will be charged
		// the transaction fee when the transaction fails.
		// It will throw HttpResponseException if account does not exist or there was another error.
		try {
			KeyPair account = KeyPair.fromAccountId(accountID);
			if(!RunningProfile.isLocal() && !checkedAccounts.contains(accountID)) {
				logger.info("Checking managed account : {}", accountID);
				checkedAccounts.add(accountID);
				Server server = stellarNetworkService.pickServer();
				server.accounts().account(account);
			}
			return account;
		} catch(IOException ex) {
			throw new AccountNotFoundException("MA(OnLoad)", accountID);
		}
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
