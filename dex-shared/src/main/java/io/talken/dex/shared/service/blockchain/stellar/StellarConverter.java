package io.talken.dex.shared.service.blockchain.stellar;

import io.talken.common.util.collection.ObjectPair;
import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeCreditAlphaNum;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StellarConverter {

	private static final BigDecimal multiplierBD = BigDecimal.valueOf(10000000);

	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

	public static BigDecimal scale(BigDecimal bd) {
		return bd.setScale(7, RoundingMode.FLOOR);
	}

	// convert
	public static BigDecimal rawToActual(long raw) {
		return rawToActual(BigInteger.valueOf(raw));
	}

	public static BigDecimal rawToActual(BigInteger raw) {
		return scale(new BigDecimal(raw)).divide(multiplierBD, RoundingMode.FLOOR);
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

	public static ObjectPair<String, String> getResultCodesFromExtra(SubmitTransactionResponse txResponse) {
		if(txResponse.getExtras() == null || txResponse.getExtras().getResultCodes() == null)
			return new ObjectPair<>("", "");

		final String errorCode = txResponse.getExtras().getResultCodes().getTransactionResultCode();
		final String errorMessage = (txResponse.getExtras().getResultCodes().getOperationsResultCodes() != null) ? String.join(",", txResponse.getExtras().getResultCodes().getOperationsResultCodes()) : "";

		return new ObjectPair<>(errorCode, errorMessage);
	}

	public static BigDecimal getAccountBalance(AccountResponse accountResponse, Asset asset) {
		if(accountResponse == null) return null;
		for(AccountResponse.Balance _bal : accountResponse.getBalances()) {
			if(_bal.getAsset().equals(asset)) {
				return scale(new BigDecimal(_bal.getBalance()));
			}
		}
		return null;
	}

	public static BigDecimal getAccountNativeBalance(AccountResponse accountResponse) {
		return getAccountBalance(accountResponse, new AssetTypeNative());
	}

	public static boolean isAccountBalanceEnough(AccountResponse accountResponse, Asset asset, BigDecimal amount) {
		final BigDecimal b = getAccountBalance(accountResponse, asset);
		if(b == null) return false;
		else return b.compareTo(amount) >= 0;
	}

	public static boolean isAccountTrusted(AccountResponse accountResponse, Asset asset) {
		if(accountResponse == null) return false;
		if(getAccountBalance(accountResponse, asset) == null) return false;
		else return true;
	}
}
