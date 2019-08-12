package io.talken.dex.shared.service.blockchain.stellar;

import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeCreditAlphaNum;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StellarConverter {

	private static final BigDecimal multiplierBD = BigDecimal.valueOf(10000000);

	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

	public static BigDecimal scale(BigDecimal bd) {
		return bd.setScale(7, BigDecimal.ROUND_UP);
	}

	// convert
	public static BigDecimal rawToActual(long raw) {
		return rawToActual(BigInteger.valueOf(raw));
	}

	public static BigDecimal rawToActual(BigInteger raw) {
		return scale(new BigDecimal(raw)).divide(multiplierBD, BigDecimal.ROUND_UP);
	}

	public static BigInteger actualToRaw(BigDecimal actual) {
		return scale(actual.multiply(multiplierBD)).toBigInteger();
	}

	// toString
	public static String actualToString(BigDecimal actual) {
		return scale(actual).stripTrailingZeros().toPlainString();
	}

	public static String rawToString(BigInteger raw) {
		return raw.toString();
	}

	// convert+toString
	public static String actualToRawString(BigDecimal actual) {
		return rawToString(actualToRaw(actual));
	}

	public static String rawToActualString(long raw) {
		return rawToActualString(BigInteger.valueOf(raw));
	}

	public static String rawToActualString(BigInteger raw) {
		return actualToString(rawToActual(raw));
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
