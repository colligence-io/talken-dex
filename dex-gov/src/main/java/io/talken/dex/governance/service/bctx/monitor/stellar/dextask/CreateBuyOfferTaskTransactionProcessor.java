package io.talken.dex.governance.service.bctx.monitor.stellar.dextask;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskCreateofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskCreateoffersellfeeRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessError;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessResult;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxReceipt;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stellar.sdk.AssetTypeCreditAlphaNum;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.xdr.ClaimOfferAtom;
import org.stellar.sdk.xdr.ManageBuyOfferResult;
import org.stellar.sdk.xdr.OperationResult;
import org.stellar.sdk.xdr.OperationType;

import java.math.BigDecimal;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_CREATEOFFER;

@Component
public class CreateBuyOfferTaskTransactionProcessor extends AbstractCreateOfferTaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateBuyOfferTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private AdminAlarmService adminAlarmService;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.OFFER_CREATE_BUY;
	}

	/**
	 * update dex_task_createOffer signTxCatchFlag
	 */
	@Override
	public DexTaskTransactionProcessResult process(Long txmId, StellarTxReceipt txResult) {
		try {
			Optional<DexTaskCreateofferRecord> opt_taskRecord = dslContext.selectFrom(DEX_TASK_CREATEOFFER).where(DEX_TASK_CREATEOFFER.TASKID.eq(txResult.getTaskId().getId())).fetchOptional();

			if(!opt_taskRecord.isPresent())
				throw new DexTaskTransactionProcessError("TaskIdNotFound");

			DexTaskCreateofferRecord taskRecord = opt_taskRecord.get();

			if(taskRecord.getSignedTxCatchFlag().equals(true))
				throw new DexTaskTransactionProcessError("CreateBuyOfferAlreadyProcessed");

			// update task as signed tx catched
			taskRecord.setSignedTxCatchFlag(true);
			taskRecord.update();

			// queue fee tasks
			queueFeeTasks(txResult);
		} catch(DexTaskTransactionProcessError error) {
			return DexTaskTransactionProcessResult.error(error);
		} catch(Exception ex) {
			return DexTaskTransactionProcessResult.error("Processing", ex);
		}
		return DexTaskTransactionProcessResult.success();
	}

	/**
	 * queue fee tasks if needed
	 * this happens on following situation
	 * 1. user A create sell offer (fee not collected yet)
	 * 2. user B claimed user A's offer (B paid buying fee)
	 * 3. user A gets USDT(pivot)
	 * 4. collect fee from A's earned USDT
	 * 5. scheduler at CreateSellOfferFeeTaskTransactionProcessor will process queue
	 *
	 * @param txResult
	 */
	private void queueFeeTasks(StellarTxReceipt txResult) {
		try {
			if(txResult.getResult().getResult().getResults() == null || txResult.getResult().getResult().getResults().length < 1) {
				logger.warn("No operationResults in txResult {}", txResult.getTxHash());
				return;
			}

			OperationResult operationResult = txResult.getResult().getResult().getResults()[0];

			if(!operationResult.getTr().getDiscriminant().equals(OperationType.MANAGE_BUY_OFFER)) {
				logger.warn("Operation #0 of {} is not manage_buy_offer", txResult.getTxHash());
				return;
			}

			ManageBuyOfferResult offerResult = operationResult.getTr().getManageBuyOfferResult();
			if(offerResult.getSuccess() == null) return;
			if(offerResult.getSuccess().getOffersClaimed() == null || offerResult.getSuccess().getOffersClaimed().length < 1)
				return;

			for(ClaimOfferAtom claimedOffer : offerResult.getSuccess().getOffersClaimed()) {
				Long offerId = claimedOffer.getOfferID().getInt64();
				org.stellar.sdk.Asset _assetSold = org.stellar.sdk.Asset.fromXdr(claimedOffer.getAssetSold());
				org.stellar.sdk.Asset _assetBought = org.stellar.sdk.Asset.fromXdr(claimedOffer.getAssetBought());

				if(_assetSold.getType().equals("native") || _assetBought.getType().equals("native")) {
					logger.warn("Native offer match detected in dexTask : offer#{}({})", offerId, txResult.getTxHash());
					continue;
				}

				String sellerAccountID = KeyPair.fromPublicKey(claimedOffer.getSellerID().getAccountID().getEd25519().getUint256()).getAccountId();
				String buyerAccountID = txResult.getTransaction().getOperations()[0].getSourceAccount();

				AssetTypeCreditAlphaNum assetSold = (AssetTypeCreditAlphaNum) _assetSold;
				AssetTypeCreditAlphaNum assetBought = (AssetTypeCreditAlphaNum) _assetBought;
				BigDecimal amountSold = StellarConverter.rawToActual(claimedOffer.getAmountSold().getInt64());
				BigDecimal amountBought = StellarConverter.rawToActual(claimedOffer.getAmountBought().getInt64());
				logger.debug("{} {} sold for {} {} : offer#{}({})", assetSold.getCode(), amountSold, assetBought.getCode(), amountBought, offerId, txResult.getTxHash());

				// someone(sellerAccount) bought assetSold with pivot asset
				if(assetBought.equals(getPivotAssetManagedInfo().dexAssetType())) {
					try {

						DexTaskCreateoffersellfeeRecord newFeeRecord = new DexTaskCreateoffersellfeeRecord();

						DexTaskId feeTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.OFFER_CREATE_SELL_FEE);
						newFeeRecord.setTaskid(feeTaskId.getId());
						newFeeRecord.setOffertxhash(txResult.getTxHash());
						newFeeRecord.setOfferid(offerId);
						newFeeRecord.setTradeaddr(sellerAccountID);
						newFeeRecord.setBuyertradeaddr(buyerAccountID);
						newFeeRecord.setSoldassetcode(assetSold.getCode());
						newFeeRecord.setSoldamount(amountSold);
						newFeeRecord.setBoughtassetcode(assetBought.getCode());
						newFeeRecord.setBoughtamount(amountBought);
						newFeeRecord.setTxStatus(BctxStatusEnum.QUEUED);

						dslContext.attach(newFeeRecord);
						newFeeRecord.store();

					} catch(Exception ex) {
						logger.exception(ex, "Cannot insert new offerSellFee record");
					}
				}
			}
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex, "Exception while queueing offerFee task for tx {}", txResult.getTxHash());
		}
	}
}
