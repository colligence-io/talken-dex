package io.colligence.talken.dex.api.dex;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.api.DexTaskId;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
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
	private DexSettings dexSettings;

	@Autowired
	private ManagedAccountService maService;

	private Asset deanchorPivotAssetType;
	private double deanchorPivotAmount;


	@PostConstruct
	private void init() throws AssetTypeNotFoundException {
		deanchorPivotAssetType = maService.getAssetType(dexSettings.getFee().getDeanchorFeePivotAsset());
		deanchorPivotAmount = dexSettings.getFee().getDeanchorFeeAmount();
	}

	public Fee calculateOfferFee(String assetCode, double amount, boolean feeByCtx) throws AssetTypeNotFoundException {
		Fee fee = new Fee();

		fee.sellAssetType = maService.getAssetType(assetCode);

		if(assetCode.equalsIgnoreCase("CTX")) {
			fee.feeAssetType = maService.getAssetType("CTX");
			fee.feeCollectorAccount = maService.getOfferFeeHolderAccount("CTX");
			// NOTE : no discount for CTX offer
			fee.feeAmount = amount * dexSettings.getFee().getOfferFeeRate();
			fee.sellAmount = amount - fee.feeAmount;
		} else {
			if(feeByCtx) {
				fee.feeAssetType = maService.getAssetType("CTX");
				fee.feeCollectorAccount = maService.getOfferFeeHolderAccount("CTX");
				fee.feeAmount = exchangeAsset(fee.sellAssetType, amount * dexSettings.getFee().getOfferFeeRate() * dexSettings.getFee().getOfferFeeRateCtxFactor(), fee.feeAssetType);
				fee.sellAmount = amount;
			} else {
				fee.feeAssetType = fee.sellAssetType;
				fee.feeCollectorAccount = maService.getOfferFeeHolderAccount(assetCode);
				fee.feeAmount = amount * dexSettings.getFee().getOfferFeeRate();
				fee.sellAmount = amount - fee.feeAmount;
			}
		}
		return fee;
	}

	public Fee calculateDeanchorFee(String assetCode, Double amount, boolean feeByCtx) throws AssetTypeNotFoundException {
		Fee fee = new Fee();

		fee.sellAssetType = maService.getAssetType(assetCode);

		if(assetCode.equalsIgnoreCase("CTX")) {
			fee.feeAssetType = maService.getAssetType("CTX");
			fee.feeCollectorAccount = maService.getDeanchorFeeHolderAccount("CTX");
			// NOTE : no discount for CTX deanchoring
			fee.feeAmount = exchangeAsset(deanchorPivotAssetType, deanchorPivotAmount, fee.feeAssetType);
			fee.sellAmount = amount - fee.feeAmount;
		} else {
			if(feeByCtx) {
				fee.feeAssetType = maService.getAssetType("CTX");
				fee.feeCollectorAccount = maService.getDeanchorFeeHolderAccount("CTX");
				fee.feeAmount = exchangeAsset(deanchorPivotAssetType, deanchorPivotAmount, fee.feeAssetType) * dexSettings.getFee().getDeanchorFeeRateCtxFactor();
				fee.sellAmount = amount;
			} else {
				fee.feeAssetType = fee.sellAssetType;
				fee.feeCollectorAccount = maService.getDeanchorFeeHolderAccount(assetCode);
				fee.feeAmount = exchangeAsset(deanchorPivotAssetType, deanchorPivotAmount, fee.feeAssetType);
				fee.sellAmount = amount - fee.feeAmount;
			}
		}
		return fee;
	}

	public double exchangeAsset(Asset from, double amount, Asset to) {
		// TODO : implement exchange routine
		return amount;
	}

	public DexTaskId createOfferFeeRefundTask(String feeCollectorAccount, String tradeWallerAddress, double refundAmount) {
		return DexTaskId.generate(DexTaskId.Type.OFFER_REFUNDFEE);
		// TODO : implement refund routine
	}

	@Getter
	public static class Fee {
		private Asset sellAssetType;
		private double sellAmount;
		private Asset feeAssetType;
		private double feeAmount;
		private KeyPair feeCollectorAccount;
	}
}
