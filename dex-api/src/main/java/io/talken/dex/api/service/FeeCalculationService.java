package io.talken.dex.api.service;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.ApiSettings;
import io.talken.dex.api.controller.dto.CalculateFeeResult;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.TokenMetaTable;
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
	private TokenMetaService tmService;

	private BigDecimal deanchorFeeAmountTalk;
	private BigDecimal offerFeeRatePivot;
	private Map<String, DexSettings._Task._Swap._SwapFee> swapFeeMap;

	private static final BigInteger MINIMUM_FEE_RAW = BigInteger.ONE;

	private static TokenMetaTable.ManagedInfo PIVOT_ASSET_MI;

	@PostConstruct
	private void init() throws TokenMetaNotFoundException, TokenMetaNotManagedException {
		deanchorFeeAmountTalk = apiSettings.getTask().getDeanchor().getFeeAmountTalk();
		offerFeeRatePivot = apiSettings.getTask().getCreateOffer().getFeeRatePivot();
		swapFeeMap = apiSettings.getTask().getSwap().getAsset();
		PIVOT_ASSET_MI = tmService.getManagedInfo(DexSettings.PIVOT_ASSET_CODE);
	}

	public CalculateFeeResult calculateSellOfferFee(String sellAssetCode, BigDecimal sellAmount, BigDecimal sellPrice) throws TokenMetaNotFoundException, TokenMetaNotManagedException, EffectiveAmountIsNegativeException {
		CalculateFeeResult rtn = new CalculateFeeResult();
		rtn.setSellAssetType(tmService.getAssetType(sellAssetCode));
		rtn.setBuyAssetType(PIVOT_ASSET_MI.dexAssetType());
		rtn.setFeeAssetType(PIVOT_ASSET_MI.dexAssetType());
		rtn.setFeeHolderAccount(PIVOT_ASSET_MI.dexOfferFeeHolderAccount());

		BigDecimal buyAmount = sellAmount.multiply(sellPrice);
		BigDecimal feeAmount = buyAmount.multiply(offerFeeRatePivot);

		if(buyAmount.compareTo(BigDecimal.ZERO) <= 0)
			throw new EffectiveAmountIsNegativeException(rtn.getBuyAssetCode(), StellarConverter.actualToString(buyAmount));

		rtn.setSellAmountRaw(StellarConverter.actualToRaw(sellAmount));
		rtn.setBuyAmountRaw(StellarConverter.actualToRaw(buyAmount));
		rtn.setFeeAmountRaw(StellarConverter.actualToRaw(feeAmount));

		return rtn;
	}

	public CalculateFeeResult calculateBuyOfferFee(String buyAssetCode, BigDecimal buyAmount, BigDecimal buyPrice) throws TokenMetaNotFoundException, TokenMetaNotManagedException {
		CalculateFeeResult rtn = new CalculateFeeResult();
		rtn.setSellAssetType(PIVOT_ASSET_MI.dexAssetType());
		rtn.setBuyAssetType(tmService.getAssetType(buyAssetCode));
		rtn.setFeeAssetType(PIVOT_ASSET_MI.dexAssetType());
		rtn.setFeeHolderAccount(PIVOT_ASSET_MI.dexOfferFeeHolderAccount());

		BigDecimal sellAmount = buyAmount.multiply(buyPrice);
		BigDecimal feeAmount = sellAmount.multiply(offerFeeRatePivot);

		rtn.setSellAmountRaw(StellarConverter.actualToRaw(sellAmount));
		rtn.setBuyAmountRaw(StellarConverter.actualToRaw(buyAmount));
		rtn.setFeeAmountRaw(StellarConverter.actualToRaw(feeAmount));

		return rtn;
	}

	public CalculateFeeResult calculateDeanchorFee(String sellAssetCode, BigDecimal amount) throws TokenMetaNotFoundException, TokenMetaNotManagedException {
		CalculateFeeResult rtn = new CalculateFeeResult();

		rtn.setSellAssetType(tmService.getAssetType(sellAssetCode));
		rtn.setFeeAssetType(tmService.getAssetType("TALK"));
		rtn.setFeeHolderAccount(tmService.getManagedInfo(rtn.getFeeAssetCode()).dexDeanchorFeeHolderAccount());

		rtn.setFeeAmountRaw(StellarConverter.actualToRaw(deanchorFeeAmountTalk));
		rtn.setSellAmountRaw(StellarConverter.actualToRaw(amount));

		return rtn;
	}

	public CalculateFeeResult calculateSwapFee(String sourceAssetCode, BigDecimal sourceAmount, String targetAssetCode) throws SwapServiceNotAvailableException, SwapUnderMinimumAmountException, TokenMetaNotFoundException, TokenMetaNotManagedException {
		DexSettings._Task._Swap._SwapFee swapFeeSetting = swapFeeMap.get(sourceAssetCode);

		CalculateFeeResult rtn = new CalculateFeeResult();
		rtn.setSellAssetType(tmService.getAssetType(sourceAssetCode));
		rtn.setSellAmountRaw(StellarConverter.actualToRaw(sourceAmount));
		rtn.setBuyAssetType(tmService.getAssetType(targetAssetCode));
		rtn.setFeeHolderAccount(tmService.getManagedInfo(rtn.getFeeAssetCode()).dexSwapFeeHolderAccount());
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
