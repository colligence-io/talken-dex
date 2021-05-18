package io.talken.dex.api.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import lombok.Data;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The type Calculate fee result.
 */
@Data
public class CalculateFeeResult {
	@JsonIgnore
	private Asset sellAssetType;
	private BigInteger sellAmountRaw;

	@JsonIgnore
	private Asset buyAssetType;
	private BigInteger buyAmountRaw;

	@JsonIgnore
	private Asset feeAssetType;
	private BigInteger feeAmountRaw;
	@JsonIgnore
	private KeyPair feeHolderAccount;

    /**
     * Gets sell asset code.
     *
     * @return the sell asset code
     */
    public String getSellAssetCode() {
		return (sellAssetType == null) ? null : StellarConverter.toAssetCode(sellAssetType);
	}

    /**
     * Gets buy asset code.
     *
     * @return the buy asset code
     */
    public String getBuyAssetCode() {
		return (buyAssetType == null) ? null : StellarConverter.toAssetCode(buyAssetType);
	}

    /**
     * Gets fee asset code.
     *
     * @return the fee asset code
     */
    public String getFeeAssetCode() {
		return (feeAssetType == null) ? null : StellarConverter.toAssetCode(feeAssetType);
	}

    /**
     * Gets sell amount.
     *
     * @return the sell amount
     */
    public BigDecimal getSellAmount() {
		return (sellAmountRaw == null) ? null : StellarConverter.rawToActual(sellAmountRaw);
	}

    /**
     * Gets buy amount.
     *
     * @return the buy amount
     */
    public BigDecimal getBuyAmount() {
		return (buyAmountRaw == null) ? null : StellarConverter.rawToActual(buyAmountRaw);
	}

    /**
     * Gets fee amount.
     *
     * @return the fee amount
     */
    public BigDecimal getFeeAmount() {
		return (feeAmountRaw == null) ? null : StellarConverter.rawToActual(feeAmountRaw);
	}

    /**
     * Gets fee holder account address.
     *
     * @return the fee holder account address
     */
    public String getFeeHolderAccountAddress() {
		return feeHolderAccount.getAccountId();
	}
}
