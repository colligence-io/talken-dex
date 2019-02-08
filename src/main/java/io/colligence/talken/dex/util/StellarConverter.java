package io.colligence.talken.dex.util;

import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeCreditAlphaNum;
import org.stellar.sdk.xdr.Int64;

public class StellarConverter {

	private static final double multiplier = 10000000;

	public static Double toDouble(Int64 value) {
		if(value == null || value.getInt64() == null) return null;
		return value.getInt64().doubleValue() / multiplier;
	}

	public static String toAssetCode(Asset assetType) {
		if(assetType.getType().equals("native")) {
			return "XLM";
		} else {
			return ((AssetTypeCreditAlphaNum) assetType).getCode();
		}
	}

	public static String toAssetCode(org.stellar.sdk.xdr.Asset assetType) {
		Asset _assetType = Asset.fromXdr(assetType);
		if(_assetType.getType().equals("native")) {
			return "XLM";
		} else {
			return ((AssetTypeCreditAlphaNum) _assetType).getCode();
		}
	}
}
