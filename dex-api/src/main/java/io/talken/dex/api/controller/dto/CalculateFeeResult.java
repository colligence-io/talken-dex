package io.talken.dex.api.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import lombok.Data;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;

import java.math.BigDecimal;
import java.math.BigInteger;

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

	public String getSellAssetCode() {
		return (sellAssetType == null) ? null : StellarConverter.toAssetCode(sellAssetType);
	}

	public String getBuyAssetCode() {
		return (buyAssetType == null) ? null : StellarConverter.toAssetCode(buyAssetType);
	}

	public String getFeeAssetCode() {
		return (feeAssetType == null) ? null : StellarConverter.toAssetCode(feeAssetType);
	}

	public BigDecimal getSellAmount() {
		return (sellAmountRaw == null) ? null : StellarConverter.rawToActual(sellAmountRaw);
	}

	public BigDecimal getBuyAmount() {
		return (buyAmountRaw == null) ? null : StellarConverter.rawToActual(buyAmountRaw);
	}

	public BigDecimal getFeeAmount() {
		return (feeAmountRaw == null) ? null : StellarConverter.rawToActual(feeAmountRaw);
	}

	public String getFeeHolderAccountAddress() {
		return feeHolderAccount.getAccountId();
	}
}
