package io.talken.dex.shared.service.blockchain.stellar;

import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeCreditAlphaNum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StellarConverter {

	private static final double multiplier = 10000000;
	private static final BigDecimal multiplierBD = BigDecimal.valueOf(multiplier);

	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

	private static BigDecimal scale(BigDecimal bd) {
		return bd.setScale(7, BigDecimal.ROUND_UP);
	}

	public static BigDecimal doubleToActual(double value) {
		return scale(new BigDecimal(Double.toString(value)));
	}

	public static long actualToRaw(double value) {
		return actualToRaw(doubleToActual(value));
	}

	public static long actualToRaw(BigDecimal value) {
		return scale(value).multiply(multiplierBD).longValue();
	}

	public static String actualToString(double value) {
		return actualToString(doubleToActual(value));
	}

	public static String actualToString(BigDecimal value) {
		return scale(value).toString();
	}

	public static BigDecimal rawToActual(long value) {
		return scale(new BigDecimal(value)).divide(multiplierBD, BigDecimal.ROUND_UP);
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
