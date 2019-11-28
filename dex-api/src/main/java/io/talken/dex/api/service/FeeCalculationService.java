package io.talken.dex.api.service;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.ApiSettings;
import io.talken.dex.api.controller.dto.CalculateFeeResult;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.exception.AssetConvertException;
import io.talken.dex.shared.exception.EffectiveAmountIsNegativeException;
import io.talken.dex.shared.exception.SwapServiceNotAvailableException;
import io.talken.dex.shared.exception.SwapUnderMinimumAmountException;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;

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

	private BigDecimal deanchorFeeAmountTalk;
	private BigDecimal offerFeeRate;
	private BigDecimal offerFeeRateTalkFactor;
	private Map<String, DexSettings._Task._Swap._SwapFee> swapFeeMap;

	private static final BigInteger MINIMUM_FEE_RAW = BigInteger.ONE;

	@PostConstruct
	private void init() {
		deanchorFeeAmountTalk = apiSettings.getTask().getDeanchor().getFeeAmountTalk();
		offerFeeRate = apiSettings.getTask().getCreateOffer().getFeeRate();
		offerFeeRateTalkFactor = apiSettings.getTask().getCreateOffer().getFeeRateTalkFactor();
		swapFeeMap = apiSettings.getTask().getSwap().getAsset();
	}

	public CalculateFeeResult calculateOfferFee(boolean isSell, String sellAssetCode, String buyAssetCode, BigDecimal amount, BigDecimal price, boolean feeByTalk) throws TokenMetaNotFoundException, AssetConvertException, EffectiveAmountIsNegativeException, TokenMetaNotManagedException {
		CalculateFeeResult rtn = new CalculateFeeResult();

		rtn.setSellAssetType(maService.getAssetType(sellAssetCode));
		rtn.setBuyAssetType(maService.getAssetType(buyAssetCode));
		rtn.setFeeAssetType((feeByTalk) ? maService.getAssetType("TALK") : rtn.getSellAssetType());
		rtn.setFeeHolderAccount(maService.getManagedInfo(rtn.getFeeAssetCode()).dexOfferFeeHolderAccount());

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
			BigInteger feeAmountRaw = StellarConverter.actualToRaw(sellAmount.multiply(offerFeeRate));
			rtn.setFeeAmountRaw(feeAmountRaw.equals(BigInteger.ZERO) ? MINIMUM_FEE_RAW : feeAmountRaw);
			calculatedSellAmountRaw = StellarConverter.actualToRaw(sellAmount).subtract(rtn.getFeeAmountRaw());
		} else {
			BigDecimal sellAssetFeeAmount = sellAmount.multiply(offerFeeRate).multiply(offerFeeRateTalkFactor);
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

	public CalculateFeeResult calculateDeanchorFee(String sellAssetCode, BigDecimal amount, boolean feeByTalk) throws TokenMetaNotFoundException, TokenMetaNotManagedException {
		CalculateFeeResult rtn = new CalculateFeeResult();

		rtn.setSellAssetType(maService.getAssetType(sellAssetCode));
		rtn.setFeeAssetType(maService.getAssetType("TALK"));
		rtn.setFeeHolderAccount(maService.getManagedInfo(rtn.getFeeAssetCode()).dexDeanchorFeeHolderAccount());

		rtn.setFeeAmountRaw(StellarConverter.actualToRaw(deanchorFeeAmountTalk));
		rtn.setSellAmountRaw(StellarConverter.actualToRaw(amount));

		return rtn;
	}

	public CalculateFeeResult calculateSwapFee(String sourceAssetCode, BigDecimal sourceAmount, String targetAssetCode) throws SwapServiceNotAvailableException, SwapUnderMinimumAmountException, TokenMetaNotFoundException, TokenMetaNotManagedException {
		DexSettings._Task._Swap._SwapFee swapFeeSetting = swapFeeMap.get(sourceAssetCode);

		CalculateFeeResult rtn = new CalculateFeeResult();
		rtn.setSellAssetType(maService.getAssetType(sourceAssetCode));
		rtn.setSellAmountRaw(StellarConverter.actualToRaw(sourceAmount));
		rtn.setBuyAssetType(maService.getAssetType(targetAssetCode));
		rtn.setFeeHolderAccount(maService.getManagedInfo(rtn.getFeeAssetCode()).dexSwapFeeHolderAccount());
		rtn.setFeeAssetType(rtn.getSellAssetType());

		// check swap fee settings for source asset
		if(swapFeeSetting == null || swapFeeSetting.getFeeRate() == null)
			throw new SwapServiceNotAvailableException(sourceAssetCode);

		// calculate fee from max/min/rate
		BigDecimal swapFee = sourceAmount.multiply(swapFeeSetting.getFeeRate());
		if(swapFeeSetting.getFeeMaximum() != null) swapFee = swapFeeSetting.getFeeMaximum().min(swapFee);

		if(swapFeeSetting.getFeeMinimum() != null && swapFee.compareTo(swapFeeSetting.getFeeMinimum()) < 0)
			throw new SwapUnderMinimumAmountException(sourceAssetCode, sourceAmount, swapFeeSetting.getFeeMinimum().divide(swapFeeSetting.getFeeRate(), 0, RoundingMode.UP));

		rtn.setFeeAmountRaw(StellarConverter.actualToRaw(swapFee));

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
