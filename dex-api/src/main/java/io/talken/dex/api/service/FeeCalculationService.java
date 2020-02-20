package io.talken.dex.api.service;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.ApiSettings;
import io.talken.dex.api.controller.dto.CalculateFeeResult;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.exception.EffectiveAmountIsNegativeException;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;

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

	private static TokenMetaTable.ManagedInfo PIVOT_ASSET_MI;

	@PostConstruct
	private void init() throws TokenMetaNotFoundException, TokenMetaNotManagedException {
		deanchorFeeAmountTalk = apiSettings.getTask().getDeanchor().getFeeAmountTalk();
		offerFeeRatePivot = apiSettings.getTask().getCreateOffer().getFeeRatePivot();
		PIVOT_ASSET_MI = tmService.getManagedInfo(DexSettings.PIVOT_ASSET_CODE);
	}

	/**
	 * pivot asset info
	 *
	 * @return
	 */
	public TokenMetaTable.ManagedInfo getPivotAssetManagedInfo() {
		return PIVOT_ASSET_MI;
	}

	/**
	 * calculate sell offer fee
	 *
	 * @param sellAssetCode
	 * @param sellAmount
	 * @param sellPrice
	 * @return
	 * @throws TokenMetaNotFoundException
	 * @throws TokenMetaNotManagedException
	 * @throws EffectiveAmountIsNegativeException
	 */
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

	/**
	 * calculate buy offer fee
	 *
	 * @param buyAssetCode
	 * @param buyAmount
	 * @param buyPrice
	 * @return
	 * @throws TokenMetaNotFoundException
	 * @throws TokenMetaNotManagedException
	 */
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

	/**
	 * calculate deanchor fee
	 *
	 * @param sellAssetCode
	 * @param amount
	 * @return
	 * @throws TokenMetaNotFoundException
	 * @throws TokenMetaNotManagedException
	 */
	public CalculateFeeResult calculateDeanchorFee(String sellAssetCode, BigDecimal amount) throws TokenMetaNotFoundException, TokenMetaNotManagedException {
		CalculateFeeResult rtn = new CalculateFeeResult();

		rtn.setSellAssetType(tmService.getAssetType(sellAssetCode));
		rtn.setFeeAssetType(tmService.getAssetType("TALK"));
		rtn.setFeeHolderAccount(tmService.getManagedInfo(rtn.getFeeAssetCode()).dexDeanchorFeeHolderAccount());

		rtn.setFeeAmountRaw(StellarConverter.actualToRaw(deanchorFeeAmountTalk));
		rtn.setSellAmountRaw(StellarConverter.actualToRaw(amount));

		return rtn;
	}
}
