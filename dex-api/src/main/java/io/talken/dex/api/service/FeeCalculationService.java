package io.talken.dex.api.service;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.ApiSettings;
import io.talken.dex.shared.StellarConverter;
import io.talken.dex.shared.exception.AssetConvertException;
import io.talken.dex.shared.exception.NotEnoughBalanceException;
import io.talken.dex.shared.exception.TokenMetaDataNotFoundException;
import io.talken.dex.shared.exception.TokenMetaLoadException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
public class FeeCalculationService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(FeeCalculationService.class);

	@Autowired
	private ApiSettings apiSettings;

	@Autowired
	private TokenMetaService maService;

	@Autowired
	private AssetConvertService assetConvertService;

	private Asset deanchorPivotAssetType;
	private long deanchorPivotAmountRaw;

	private static final long MINIMUM_FEE_RAW = 1;

	@PostConstruct
	private void init() throws TokenMetaDataNotFoundException {
		deanchorPivotAssetType = maService.getAssetType(apiSettings.getFee().getDeanchorFeePivotAsset());
		deanchorPivotAmountRaw = StellarConverter.actualToRaw(apiSettings.getFee().getDeanchorFeeAmount());
	}

	public Fee calculateOfferFee(String assetCode, long amountRaw, boolean feeByCtx) throws NotEnoughBalanceException, TokenMetaDataNotFoundException, AssetConvertException, TokenMetaLoadException {
		Fee fee = new Fee();

		fee.sellAssetType = maService.getAssetType(assetCode);

		if(assetCode.equalsIgnoreCase("CTX")) {
			fee.feeAssetType = maService.getAssetType("CTX");
			fee.feeCollectorAccount = maService.getOfferFeeHolderAccount("CTX");
			// NOTE : no discount for CTX offer
			fee.feeAmountRaw = (long) (amountRaw * apiSettings.getFee().getOfferFeeRate());
			if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
			fee.sellAmountRaw = amountRaw - fee.feeAmountRaw;
		} else {
			if(feeByCtx) {
				fee.feeAssetType = maService.getAssetType("CTX");
				fee.feeCollectorAccount = maService.getOfferFeeHolderAccount("CTX");
				fee.feeAmountRaw = assetConvertService.convertRaw(fee.sellAssetType, (long) (amountRaw * apiSettings.getFee().getOfferFeeRate() * apiSettings.getFee().getOfferFeeRateCtxFactor()), fee.feeAssetType);
				if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
				fee.sellAmountRaw = amountRaw;
			} else {
				fee.feeAssetType = fee.sellAssetType;
				fee.feeCollectorAccount = maService.getOfferFeeHolderAccount(assetCode);
				fee.feeAmountRaw = (long) (amountRaw * apiSettings.getFee().getOfferFeeRate());
				if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
				fee.sellAmountRaw = amountRaw - fee.feeAmountRaw;
			}
		}

		if(fee.sellAmountRaw <= 0)
			throw new NotEnoughBalanceException(fee.feeAssetType.getType(), StellarConverter.rawToActualString(fee.feeAmountRaw));

		return fee;
	}

	public Fee calculateDeanchorFee(String assetCode, long amountRaw, boolean feeByCtx) throws NotEnoughBalanceException, TokenMetaDataNotFoundException, AssetConvertException, TokenMetaLoadException {
		Fee fee = new Fee();

		fee.sellAssetType = maService.getAssetType(assetCode);

		if(assetCode.equalsIgnoreCase("CTX")) {
			fee.feeAssetType = maService.getAssetType("CTX");
			fee.feeCollectorAccount = maService.getDeanchorFeeHolderAccount("CTX");
			// NOTE : no discount for CTX deanchoring
			fee.feeAmountRaw = assetConvertService.convertRaw(deanchorPivotAssetType, deanchorPivotAmountRaw, fee.feeAssetType);
			if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
			fee.sellAmountRaw = amountRaw - fee.feeAmountRaw;
		} else {
			if(feeByCtx) {
				fee.feeAssetType = maService.getAssetType("CTX");
				fee.feeCollectorAccount = maService.getDeanchorFeeHolderAccount("CTX");
				fee.feeAmountRaw = (long) (assetConvertService.convertRaw(deanchorPivotAssetType, deanchorPivotAmountRaw, fee.feeAssetType) * apiSettings.getFee().getDeanchorFeeRateCtxFactor());
				if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
				fee.sellAmountRaw = amountRaw;
			} else {
				fee.feeAssetType = fee.sellAssetType;
				fee.feeCollectorAccount = maService.getDeanchorFeeHolderAccount(assetCode);
				fee.feeAmountRaw = assetConvertService.convertRaw(deanchorPivotAssetType, deanchorPivotAmountRaw, fee.feeAssetType);
				if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
				fee.sellAmountRaw = amountRaw - fee.feeAmountRaw;
			}
		}

		if(fee.sellAmountRaw <= 0)
			throw new NotEnoughBalanceException(fee.feeAssetType.getType(), StellarConverter.rawToActualString(fee.feeAmountRaw));

		return fee;
	}

	@Getter
	public static class Fee {
		private Asset sellAssetType;
		private long sellAmountRaw;
		private Asset feeAssetType;
		private long feeAmountRaw;
		private KeyPair feeCollectorAccount;
	}
}
