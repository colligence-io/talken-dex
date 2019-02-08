package io.colligence.talken.dex.api.mas.ma;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.colligence.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.colligence.talken.common.util.collection.SingleKeyObject;
import lombok.Data;
import org.stellar.sdk.AssetTypeCreditAlphaNum4;
import org.stellar.sdk.KeyPair;

import java.util.ArrayList;
import java.util.List;

@Data
public class ManagedAccountPack implements SingleKeyObject<String> {
	private String code;
	private BlockChainPlatformEnum platform;
	private String assetIssuerAddress;
	@JsonIgnore
	private KeyPair assetIssuer;
	private String assetBaseAddress;
	@JsonIgnore
	private KeyPair assetBase;
	private List<AssetHolder> assetHolder = new ArrayList<>();
	private String offerFeeHolderAddress;
	@JsonIgnore
	private KeyPair offerFeeHolder;
	private String deanchorFeeHolderAddress;
	@JsonIgnore
	private KeyPair deanchorFeeHolder;
	private String assetCode;
	@JsonIgnore
	private AssetTypeCreditAlphaNum4 assetType;

	@Override
	public String __getSKey__() {
		return this.code;
	}

	public String getPlatformTxTunnelType() {
		if(platform != null) return platform.getPlatformTxTunnelType();
		else return null;
	}

	public void addAssetHolder(String address) {
		assetHolder.add(new AssetHolder(address, true));
	}

	public void addAssetHolder(String address, boolean isHot) {
		addAssetHolder(address, isHot);
	}

	@Data
	public static class AssetHolder {
		private String address;
		private boolean isHot;
		private boolean isActive;

		public AssetHolder(String address, boolean isHot) {
			this.address = address;
			this.isHot = isHot;
			this.isActive = false;
		}
	}
}
