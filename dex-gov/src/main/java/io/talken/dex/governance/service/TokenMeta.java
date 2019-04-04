package io.talken.dex.governance.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.talken.common.persistence.enums.LangTypeEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.pojos.TokenEntry;
import io.talken.common.persistence.jooq.tables.pojos.TokenMetaManaged;
import io.talken.common.persistence.jooq.tables.pojos.TokenMetaManagedHolder;
import io.talken.common.persistence.jooq.tables.pojos.TokenMetaManagedMarketpair;
import io.talken.common.util.collection.SingleKeyObject;
import lombok.Getter;
import lombok.Setter;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class TokenMeta extends io.talken.common.persistence.jooq.tables.pojos.TokenMeta implements SingleKeyObject<String> {
	private static final long serialVersionUID = -6951210186179531241L;

	@JsonIgnore
	@Override
	public String __getSKey__() {
		return getSymbol().toUpperCase();
	}

	private Map<LangTypeEnum, String> name;
	private Map<LangTypeEnum, EntryInfo> entryInfo;
	private ManagedInfo managedInfo = null;
	private Map<TokenMetaAuxCodeEnum, Object> aux;
	private Map<String, Double> exchangeRate;

	@Getter
	@Setter
	public static class EntryInfo extends TokenEntry {
		private static final long serialVersionUID = 5249971212423154448L;

		@Override
		public Long getId() {
			return super.getId();
		}
	}

	@Getter
	@Setter
	public static class ManagedInfo extends TokenMetaManaged {
		private static final long serialVersionUID = -4332284054048595977L;

		private String assetCode;
		private Asset assetType;
		private KeyPair assetIssuer;
		private KeyPair assetBase;
		private KeyPair offerFeeHolder;
		private KeyPair deanchorFeeHolder;

		private Map<String, MarketPairInfo> marketPair;
		private List<HolderAccountInfo> assetHolderAccounts;
	}

	@Getter
	@Setter
	public static class MarketPairInfo extends TokenMetaManagedMarketpair {
		private static final long serialVersionUID = -5476500764748414007L;
	}

	@Getter
	@Setter
	public static class HolderAccountInfo extends TokenMetaManagedHolder {
		private static final long serialVersionUID = -8628865742603838879L;
	}
}
