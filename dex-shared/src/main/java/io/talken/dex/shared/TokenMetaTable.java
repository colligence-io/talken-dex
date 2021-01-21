package io.talken.dex.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.RegionEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.enums.TokenMetaManagedStatusEnum;
import io.talken.dex.shared.exception.ActiveAssetHolderAccountNotFoundException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

@Data
@EqualsAndHashCode(callSuper = false)
public class TokenMetaTable extends HashMap<String, TokenMetaTable.Meta> implements Serializable {
	private static final long serialVersionUID = -3910073165659722969L;

	public static final String REDIS_KEY = "talken:svc:token_meta";
	public static final String REDIS_UPDATED_KEY = "talken:svc:token_meta_updated";

	public Meta forMeta(String key) {
		if(!this.containsKey(key)) this.put(key, new Meta());
		return this.get(key);
	}

	@Data
	public static class Meta {
		private Long id;
		private String nameKey;
		private String symbol;
		private String platform;
		private BlockChainPlatformEnum bctxType;
		private Boolean nativeFlag;
		private String iconUrl;
		private String thumbnailUrl;
		private Integer viewUnitExpn;
		private Integer unitDecimals;
		private Integer cmcId;
		private Map<RegionEnum, String> name = new HashMap<>();
		private Map<RegionEnum, EntryInfo> entryInfo;
		private Map<TokenMetaAuxCodeEnum, Object> aux;
		private Map<String, BigDecimal> exchangeRate;
		private ManagedInfo managedInfo = null;
		private Long updateTimestamp;

		@JsonIgnore
		public boolean isManaged() {
			return managedInfo != null;
		}

		public EntryInfo forEntry(RegionEnum region) {
			if(entryInfo == null) entryInfo = new HashMap<>();
			if(!entryInfo.containsKey(region)) entryInfo.put(region, new EntryInfo());
			return entryInfo.get(region);
		}
	}

	@Data
	public static class EntryInfo {
		private Long id;
		private String name;
		private Integer revision;
		private Integer numFollower;
		private Boolean showFlag;
		private Boolean confirmFlag;
		private Long updateTimestamp;
	}

	@Data
	public static class ManagedInfo {
		private String assetCode;
		private String issuerAddress;
		private String offerFeeHolderAddress;
		private String deancFeeHolderAddress;
		private String swapFeeHolderAddress;
		private String distributorAddress;

        private Boolean privateWalletUsableFlag;
        private Boolean tradeWalletUsableFlag;
        private Boolean sendableFlag;
        private Boolean anchorableFlag;
        private Boolean deanchorableFlag;
        private TokenMetaManagedStatusEnum managedStatus;

		private Map<String, MarketPairInfo> marketPair = null;
		private List<HolderAccountInfo> assetHolderAccounts = null;
		private Long updateTimestamp;

		@JsonIgnore
		private StellarCache cache;

		public void prepareCache() {
			cache = new StellarCache(this);
		}

		public MarketPairInfo forMarketPair(String symbol) {
			if(marketPair == null) marketPair = new HashMap<>();
			if(!marketPair.containsKey(symbol)) marketPair.put(symbol, new MarketPairInfo());
			return marketPair.get(symbol);
		}

		public HolderAccountInfo newHolderAccountInfo() {
			if(assetHolderAccounts == null) assetHolderAccounts = new ArrayList<>();
			HolderAccountInfo rtn = new HolderAccountInfo();
			assetHolderAccounts.add(rtn);
			return rtn;
		}

		public Asset dexAssetType() {
			return cache.getAssetType();
		}

		public KeyPair dexOfferFeeHolderAccount() {
			return cache.getOfferFeeHolder();
		}

		public KeyPair dexDeanchorFeeHolderAccount() {
			return cache.getDeanchorFeeHolder();
		}

		public KeyPair dexSwapFeeHolderAccount() {
			return cache.getSwapFeeHolder();
		}

		public KeyPair dexIssuerAccount() {
			return cache.getAssetIssuer();
		}

		// TODO : ??? activeFlag, hotFlag
        // TODO : anchor/deanchor 요청 제어
		public String pickActiveHolderAccountAddress() throws ActiveAssetHolderAccountNotFoundException {
			Optional<HolderAccountInfo> opt_aha = assetHolderAccounts.stream()
					.filter(TokenMetaTable.HolderAccountInfo::getActiveFlag)
					.findAny();
			if(opt_aha.isPresent()) return opt_aha.get().getAddress();
			else {
				Optional<TokenMetaTable.HolderAccountInfo> opt_ahh = assetHolderAccounts.stream()
						.filter(TokenMetaTable.HolderAccountInfo::getHotFlag)
						.findAny();
				if(opt_ahh.isPresent()) return opt_ahh.get().getAddress();
				else throw new ActiveAssetHolderAccountNotFoundException(assetCode);
			}
		}
	}

	@Getter
	public static class StellarCache {
		private StellarCache(ManagedInfo mi) {
			assetIssuer = KeyPair.fromAccountId(mi.issuerAddress);
			assetType = Asset.createNonNativeAsset(mi.assetCode, assetIssuer.getAccountId());
			offerFeeHolder = KeyPair.fromAccountId(mi.offerFeeHolderAddress);
			deanchorFeeHolder = KeyPair.fromAccountId(mi.deancFeeHolderAddress);
			swapFeeHolder = KeyPair.fromAccountId(mi.swapFeeHolderAddress);
		}

		private Asset assetType;
		private KeyPair assetIssuer;
		private KeyPair offerFeeHolder;
		private KeyPair deanchorFeeHolder;
		private KeyPair swapFeeHolder;
	}

	@Data
	public static class MarketPairInfo {
		private Boolean activeFlag;
		private Integer tradeUnitExpn;
		private Long aggrTimestamp;
		private Integer tradeCount;
		private BigDecimal baseVolume;
		private BigDecimal counterVolume;
		private BigDecimal priceAvg;
		private BigDecimal priceH;
		private BigDecimal priceL;
		private BigDecimal priceO;
		private BigDecimal priceC;
		private Long updateTimestamp;
	}

	@Data
	public static class HolderAccountInfo {
		private String address;
		private Boolean activeFlag;
		private Boolean hotFlag;
	}
}
