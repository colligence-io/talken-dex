package io.talken.dex.api.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.talken.common.persistence.enums.LangTypeEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.pojos.*;
import io.talken.common.util.collection.SingleKeyObject;
import lombok.Getter;
import lombok.Setter;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties({"createTimestamp", "refurls"})
public class TokenMetaData extends TokenMeta implements SingleKeyObject<String> {
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
	@JsonIgnoreProperties({"id", "tokenMetaId", "langcode", "createTimestamp"})
	public static class EntryInfo extends TokenEntry {
		private static final long serialVersionUID = 5249971212423154448L;

		@Override
		@JsonProperty("tokenEntryId")
		public Long getId() {
			return super.getId();
		}
	}

	@Getter
	@Setter
	@JsonIgnoreProperties({"tokenMetaId", "createTimestamp"})
	public static class ManagedInfo extends TokenManagedInfo {
		private static final long serialVersionUID = -4332284054048595977L;

		@JsonIgnore
		private String assetCode;
		@JsonIgnore
		private Asset assetType;
		@JsonIgnore
		private KeyPair assetIssuer;
		@JsonIgnore
		private KeyPair assetBase;
		@JsonIgnore
		private KeyPair offerFeeHolder;
		@JsonIgnore
		private KeyPair deanchorFeeHolder;

		private Map<String, MarketPairInfo> marketPair;
		private List<HolderAccountInfo> assetHolderAccounts;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties({"id", "tokenMetaId", "counterMetaId", "createTimestamp"})
	public static class MarketPairInfo extends TokenManagedMarketPair {
		private static final long serialVersionUID = -5476500764748414007L;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties({"id", "tokenMetaId", "createTimestamp", "updateTimestamp"})
	public static class HolderAccountInfo extends TokenManagedHolder {
		private static final long serialVersionUID = -8628865742603838879L;
	}
}
