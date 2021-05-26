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

/**
 * Convert stellar network values Raw(BigInteger) between Actual(BigDecimal)
 */
public class StellarConverter {

	private static final BigDecimal multiplierBD = BigDecimal.valueOf(10000000);

	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

    /**
     * Scale big decimal.
     *
     * @param bd the bd
     * @return the big decimal
     */
    public static BigDecimal scale(BigDecimal bd) {
		return bd.setScale(7, RoundingMode.FLOOR);
	}

    /**
     * Raw to actual big decimal.
     *
     * @param raw the raw
     * @return the big decimal
     */
// convert
	public static BigDecimal rawToActual(long raw) {
		return rawToActual(BigInteger.valueOf(raw));
	}

    /**
     * Raw to actual big decimal.
     *
     * @param raw the raw
     * @return the big decimal
     */
    public static BigDecimal rawToActual(BigInteger raw) {
		return scale(new BigDecimal(raw)).divide(multiplierBD, RoundingMode.FLOOR);
	}

    /**
     * Actual to raw big integer.
     *
     * @param actual the actual
     * @return the big integer
     */
    public static BigInteger actualToRaw(BigDecimal actual) {
		return scale(actual.multiply(multiplierBD)).toBigInteger();
	}

    /**
     * Actual to string string.
     *
     * @param actual the actual
     * @return the string
     */
// toString
	public static String actualToString(BigDecimal actual) {
		return scale(actual).stripTrailingZeros().toPlainString();
	}

    /**
     * Raw to string string.
     *
     * @param raw the raw
     * @return the string
     */
    public static String rawToString(BigInteger raw) {
		return raw.toString();
	}

    /**
     * Actual to raw string string.
     *
     * @param actual the actual
     * @return the string
     */
// convert+toString
	public static String actualToRawString(BigDecimal actual) {
		return rawToString(actualToRaw(actual));
	}

    /**
     * Raw to actual string string.
     *
     * @param raw the raw
     * @return the string
     */
    public static String rawToActualString(long raw) {
		return rawToActualString(BigInteger.valueOf(raw));
	}

    /**
     * Raw to actual string string.
     *
     * @param raw the raw
     * @return the string
     */
    public static String rawToActualString(BigInteger raw) {
		return actualToString(rawToActual(raw));
	}


    /**
     * To asset code string.
     *
     * @param assetType the asset type
     * @return the string
     */
    public static String toAssetCode(Asset assetType) {
		if(assetType.getType().equals("native")) {
			return "XLM";
		} else {
			return ((AssetTypeCreditAlphaNum) assetType).getCode();
		}
	}

    /**
     * To asset code string.
     *
     * @param assetType the asset type
     * @return the string
     */
    public static String toAssetCode(org.stellar.sdk.xdr.Asset assetType) {
		return toAssetCode(Asset.fromXdr(assetType));
	}

    /**
     * To local date time local date time.
     *
     * @param timeString the time string
     * @return the local date time
     */
    public static LocalDateTime toLocalDateTime(String timeString) {
		return LocalDateTime.parse(timeString, dtf);
	}

    /**
     * Gets result codes from extra.
     *
     * @param txResponse the tx response
     * @return the result codes from extra
     */
    public static ObjectPair<String, String> getResultCodesFromExtra(SubmitTransactionResponse txResponse) {
		if(txResponse.getExtras() == null || txResponse.getExtras().getResultCodes() == null)
			return new ObjectPair<>("", "");

		final String errorCode = txResponse.getExtras().getResultCodes().getTransactionResultCode();
		final String errorMessage = (txResponse.getExtras().getResultCodes().getOperationsResultCodes() != null) ? String.join(",", txResponse.getExtras().getResultCodes().getOperationsResultCodes()) : "";

		return new ObjectPair<>(errorCode, errorMessage);
	}

    /**
     * Gets account balance.
     *
     * @param accountResponse the account response
     * @param asset           the asset
     * @return the account balance
     */
    public static BigDecimal getAccountBalance(AccountResponse accountResponse, Asset asset) {
		if(accountResponse == null) return null;
		for(AccountResponse.Balance _bal : accountResponse.getBalances()) {
			if(_bal.getAsset().equals(asset)) {
				return scale(new BigDecimal(_bal.getBalance()));
			}
		}
		return null;
	}

    /**
     * Gets account native balance.
     *
     * @param accountResponse the account response
     * @return the account native balance
     */
    public static BigDecimal getAccountNativeBalance(AccountResponse accountResponse) {
		return getAccountBalance(accountResponse, new AssetTypeNative());
	}

    /**
     * Is account balance enough boolean.
     *
     * @param accountResponse the account response
     * @param asset           the asset
     * @param amount          the amount
     * @return the boolean
     */
    public static boolean isAccountBalanceEnough(AccountResponse accountResponse, Asset asset, BigDecimal amount) {
		final BigDecimal b = getAccountBalance(accountResponse, asset);
		if(b == null) return false;
		else return b.compareTo(amount) >= 0;
	}

    /**
     * Is account trusted boolean.
     *
     * @param accountResponse the account response
     * @param asset           the asset
     * @return the boolean
     */
    public static boolean isAccountTrusted(AccountResponse accountResponse, Asset asset) {
		if(accountResponse == null) return false;
		if(getAccountBalance(accountResponse, asset) == null) return false;
		else return true;
	}
}
