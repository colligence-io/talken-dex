package io.colligence.talken.dex.util;

import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeCreditAlphaNum;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StellarConverter {

	private static final double multiplier = 10000000;

	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

	public static double rawToDouble(long value) {
		return ((double) value) / multiplier;
	}

	public static long doubleToRaw(double value) {
		return (long) (value * multiplier);
	}

	public static String doubleToString(double value) {
		return String.format("%.7f", value);
	}

	public static String rawToDoubleString(long value) {
		return doubleToString(rawToDouble(value));
	}

	public static String toAssetCode(Asset assetType) {
		if(assetType.getType().equals("native")) {
			return "XLM";
		} else {
			return ((AssetTypeCreditAlphaNum) assetType).getCode();
		}
	}

	public static String toAssetCode(org.stellar.sdk.xdr.Asset assetType) {
		return toAssetCode(Asset.fromXdr(assetType));
	}

	public static LocalDateTime toLocalDateTime(String timeString) {
		return LocalDateTime.parse(timeString, dtf);
	}
}
