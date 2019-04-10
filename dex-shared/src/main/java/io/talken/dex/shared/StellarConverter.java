package io.talken.dex.shared;

import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeCreditAlphaNum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StellarConverter {

	private static final double multiplier = 10000000;
	private static final BigDecimal multiplierBD = BigDecimal.valueOf(10000000);

	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

	public static long actualToRaw(double value) {
		return actualToRaw(new BigDecimal(value));
	}

	public static long actualToRaw(BigDecimal value) {
		return value.multiply(multiplierBD).longValue();
	}

	public static String actualToString(double value) {
		return actualToString(new BigDecimal(value));
	}

	public static String actualToString(BigDecimal value) {
		return value.setScale(7, BigDecimal.ROUND_UP).toString();//String.format("%.7f", value);
	}

	public static BigDecimal rawToActual(long value) {
		return new BigDecimal(value).divide(multiplierBD, BigDecimal.ROUND_UP);
	}

	public static String rawToActualString(long value) {
		return actualToString(rawToActual(value));
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
