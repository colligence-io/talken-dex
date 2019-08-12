package io.talken.dex.api.service;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.ApiSettings;
import io.talken.dex.api.controller.dto.CalculateFeeResult;
import io.talken.dex.shared.exception.AssetConvertException;
import io.talken.dex.shared.exception.EffectiveAmountIsNegativeException;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

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
	private BigDecimal deanchorPivotAmount;

	private static final BigInteger MINIMUM_FEE_RAW = BigInteger.ONE;

	@PostConstruct
	private void init() throws TokenMetaNotFoundException {
		deanchorPivotAssetType = maService.getAssetType(apiSettings.getFee().getDeanchorFeePivotAsset());
		deanchorPivotAmount = StellarConverter.scale(apiSettings.getFee().getDeanchorFeeAmount());
	}


	public CalculateFeeResult calculateOfferFee(boolean isSell, String sellAssetCode, String buyAssetCode, BigDecimal amount, BigDecimal price, boolean feeByTalk) throws TokenMetaNotFoundException, AssetConvertException, EffectiveAmountIsNegativeException {
		CalculateFeeResult rtn = new CalculateFeeResult();

		rtn.setSellAssetType(maService.getAssetType(sellAssetCode));
		rtn.setBuyAssetType(maService.getAssetType(buyAssetCode));
		rtn.setFeeAssetType((feeByTalk) ? maService.getAssetType("TALK") : rtn.getSellAssetType());
		rtn.setFeeHolderAccount(maService.getOfferFeeHolderAccount(rtn.getFeeAssetCode()));

		BigDecimal sellAmount;
		BigDecimal sellPrice;
		if(isSell) {
			sellPrice = price;
			sellAmount = StellarConverter.scale(amount);
		} else {
			sellPrice = BigDecimal.ONE.setScale(18, BigDecimal.ROUND_UP).divide(price, BigDecimal.ROUND_UP);
			sellAmount = StellarConverter.scale(amount.multiply(price));
		}
		BigInteger calculatedSellAmountRaw;

		// normal fee rate and no discount when selling TALK
		if(sellAssetCode.equalsIgnoreCase("TALK") || !feeByTalk) {
			BigInteger feeAmountRaw = StellarConverter.actualToRaw(sellAmount.multiply(apiSettings.getFee().getOfferFeeRate()));
			rtn.setFeeAmountRaw(feeAmountRaw.equals(BigInteger.ZERO) ? MINIMUM_FEE_RAW : feeAmountRaw);
			calculatedSellAmountRaw = StellarConverter.actualToRaw(sellAmount).subtract(rtn.getFeeAmountRaw());
		} else {
			BigDecimal sellAssetFeeAmount = sellAmount.multiply(apiSettings.getFee().getOfferFeeRate()).multiply(apiSettings.getFee().getOfferFeeRateTalkFactor());
			BigDecimal talkFeeAmount = assetConvertService.convert(rtn.getSellAssetType(), sellAssetFeeAmount, rtn.getFeeAssetType());
			// TODO : handle when fee is zero
			rtn.setFeeAmountRaw(StellarConverter.actualToRaw(talkFeeAmount));
			calculatedSellAmountRaw = StellarConverter.actualToRaw(sellAmount);
		}

		if(calculatedSellAmountRaw.compareTo(BigInteger.ZERO) == 0)
			throw new EffectiveAmountIsNegativeException(rtn.getFeeAssetCode(), StellarConverter.actualToString(rtn.getFeeAmount()));

		rtn.setSellAmountRaw(calculatedSellAmountRaw);
		rtn.setBuyAmountRaw(StellarConverter.actualToRaw(StellarConverter.rawToActual(calculatedSellAmountRaw).multiply(sellPrice)));

		return rtn;
	}

	public CalculateFeeResult calculateDeanchorFee(String sellAssetCode, BigDecimal amount, boolean feeByTalk) throws TokenMetaNotFoundException, AssetConvertException, EffectiveAmountIsNegativeException {
		CalculateFeeResult rtn = new CalculateFeeResult();

		rtn.setSellAssetType(maService.getAssetType(sellAssetCode));
		rtn.setFeeAssetType((feeByTalk) ? maService.getAssetType("TALK") : rtn.getSellAssetType());
		rtn.setFeeHolderAccount(maService.getDeanchorFeeHolderAccount(rtn.getFeeAssetCode()));

		BigInteger calculatedAmountRaw;

		// normal fee rate and no discount when deanchor TALK
		if(sellAssetCode.equalsIgnoreCase("TALK") || !feeByTalk) {
			BigInteger feeAmountRaw = StellarConverter.actualToRaw(assetConvertService.convert(deanchorPivotAssetType, deanchorPivotAmount, rtn.getFeeAssetType()));
			rtn.setFeeAmountRaw(feeAmountRaw.equals(BigInteger.ZERO) ? MINIMUM_FEE_RAW : feeAmountRaw);
			calculatedAmountRaw = StellarConverter.actualToRaw(amount).subtract(rtn.getFeeAmountRaw());
		} else {
			BigDecimal talkFeeAmount = assetConvertService.convert(deanchorPivotAssetType, deanchorPivotAmount, rtn.getFeeAssetType()).multiply(apiSettings.getFee().getDeanchorFeeRateTalkFactor());
			// TODO : handle when fee is zero
			rtn.setFeeAmountRaw(StellarConverter.actualToRaw(talkFeeAmount));
			calculatedAmountRaw = StellarConverter.actualToRaw(amount);
		}

		if(calculatedAmountRaw.compareTo(BigInteger.ZERO) == 0)
			throw new EffectiveAmountIsNegativeException(rtn.getFeeAssetCode(), StellarConverter.actualToString(rtn.getFeeAmount()));

		rtn.setSellAmountRaw(calculatedAmountRaw);

		return rtn;
	}


//	public Fee calculateOfferFee(String assetCode, long amountRaw, boolean feeByTalk) throws TokenMetaNotFoundException, AssetConvertException, EffectiveAmountIsNegativeException {
//		Fee fee = new Fee();
//
//		fee.sellAssetType = maService.getAssetType(assetCode);
//
//		if(assetCode.equalsIgnoreCase("TALK")) {
//			// NOTE : no discount for CTX offer
//			fee.feeAmountRaw = (long) (amountRaw * apiSettings.getFee().getOfferFeeRate());
//			if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
//			fee.sellAmountRaw = amountRaw - fee.feeAmountRaw;
//		} else {
//			if(feeByTalk) {
//				fee.feeAmountRaw = assetConvertService.convertRaw(fee.sellAssetType, (long) (amountRaw * apiSettings.getFee().getOfferFeeRate() * apiSettings.getFee().getOfferFeeRateCtxFactor()), fee.feeAssetType);
//				if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
//				fee.sellAmountRaw = amountRaw;
//			} else {
//				fee.feeAmountRaw = (long) (amountRaw * apiSettings.getFee().getOfferFeeRate());
//				if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
//				fee.sellAmountRaw = amountRaw - fee.feeAmountRaw;
//			}
//		}
//
//		if(fee.sellAmountRaw <= 0)
//			throw new EffectiveAmountIsNegativeException(StellarConverter.toAssetCode(fee.feeAssetType), StellarConverter.rawToActualString(fee.feeAmountRaw));
//
//		return fee;
//	}
//
//	public Fee calculateDeanchorFee(String assetCode, long amountRaw, boolean feeByTalk) throws TokenMetaNotFoundException, AssetConvertException, EffectiveAmountIsNegativeException {
//		Fee fee = new Fee();
//
//		fee.sellAssetType = maService.getAssetType(assetCode);
//
//		if(assetCode.equalsIgnoreCase("TALK")) {
//			fee.feeAssetType = maService.getAssetType("TALK");
//			fee.feeCollectorAccount = maService.getDeanchorFeeHolderAccount("TALK");
//			// NOTE : no discount for CTX deanchoring
//			fee.feeAmountRaw = assetConvertService.convertRaw(deanchorPivotAssetType, deanchorPivotAmountRaw, fee.feeAssetType);
//			if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
//			fee.sellAmountRaw = amountRaw - fee.feeAmountRaw;
//		} else {
//			if(feeByTalk) {
//				fee.feeAssetType = maService.getAssetType("TALK");
//				fee.feeCollectorAccount = maService.getDeanchorFeeHolderAccount("TALK");
//				fee.feeAmountRaw = (long) (assetConvertService.convertRaw(deanchorPivotAssetType, deanchorPivotAmountRaw, fee.feeAssetType) * apiSettings.getFee().getDeanchorFeeRateCtxFactor());
//				if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
//				fee.sellAmountRaw = amountRaw;
//			} else {
//				fee.feeAssetType = fee.sellAssetType;
//				fee.feeCollectorAccount = maService.getDeanchorFeeHolderAccount(assetCode);
//				fee.feeAmountRaw = assetConvertService.convertRaw(deanchorPivotAssetType, deanchorPivotAmountRaw, fee.feeAssetType);
//				if(fee.feeAmountRaw == 0) fee.feeAmountRaw = MINIMUM_FEE_RAW;
//				fee.sellAmountRaw = amountRaw - fee.feeAmountRaw;
//			}
//		}
//
//		if(fee.sellAmountRaw <= 0)
//			throw new EffectiveAmountIsNegativeException(StellarConverter.toAssetCode(fee.feeAssetType), StellarConverter.rawToActualString(fee.feeAmountRaw));
//
//		return fee;
//	}

}
