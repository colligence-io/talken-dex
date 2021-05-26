package io.talken.dex.governance.service.bctx.monitor.stellar.dextask;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.GeneralException;
import io.talken.common.persistence.DexTaskRecord;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.DexTaskCreateoffer;
import io.talken.common.persistence.jooq.tables.records.DexTaskCreateoffersellfeeRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessError;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessResult;
import io.talken.dex.shared.exception.StellarException;
import io.talken.dex.shared.service.blockchain.stellar.*;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_CREATEOFFER;
import static io.talken.common.persistence.jooq.Tables.DEX_TASK_CREATEOFFERSELLFEE;

/**
 * The type Create sell offer fee task transaction processor.
 */
@Component
public class CreateSellOfferFeeTaskTransactionProcessor extends AbstractCreateOfferTaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateSellOfferFeeTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private TradeWalletService twService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.OFFER_CREATE_SELL_FEE;
	}

	/**
	 * update dex_task_createOffer signTxCatchFlag
	 */
	@Override
	public DexTaskTransactionProcessResult process(Long txmId, StellarTxReceipt txResult) {
		try {
			Optional<DexTaskCreateoffersellfeeRecord> opt_taskRecord = dslContext.selectFrom(DEX_TASK_CREATEOFFERSELLFEE).where(DEX_TASK_CREATEOFFERSELLFEE.TASKID.eq(txResult.getTaskId().getId())).fetchOptional();

			if(!opt_taskRecord.isPresent())
				throw new DexTaskTransactionProcessError("TaskIdNotFound");

			DexTaskCreateoffersellfeeRecord taskRecord = opt_taskRecord.get();

			if(taskRecord.getSignedTxCatchFlag().equals(true))
				return DexTaskTransactionProcessResult.success();

			// update task as signed tx catched
			taskRecord.setSignedTxCatchFlag(true);
			taskRecord.update();

		} catch(DexTaskTransactionProcessError error) {
			return DexTaskTransactionProcessResult.error(error);
		} catch(Exception ex) {
			return DexTaskTransactionProcessResult.error("Processing", ex);
		}
		return DexTaskTransactionProcessResult.success();
	}

	/**
	 * Process queued fee tasks at dex_task_createSellOfferFee (queued from CreateBuyOfferTaskTransactionProcessor)
	 */
	@Scheduled(fixedDelay = 5000, initialDelay = 5000)
	private void checkFee() {
		if(DexGovStatus.isStopped) return;

		Result<DexTaskCreateoffersellfeeRecord> feeRecords = dslContext.selectFrom(DEX_TASK_CREATEOFFERSELLFEE)
				.where(DEX_TASK_CREATEOFFERSELLFEE.TX_STATUS.eq(BctxStatusEnum.QUEUED))
				.fetch();


		for(DexTaskCreateoffersellfeeRecord feeRecord : feeRecords) {
			String position = "inspect_wallet";
			try {
				feeRecord.setTxStatus(BctxStatusEnum.SENT); // make sure do not send again

				TradeWalletInfo tradeWallet = twService.getTradeWallet(feeRecord.getTradeaddr());

				feeRecord.setUserId(tradeWallet.getUser().getId());

				position = "inspect_crof";
				DexTaskCreateoffer dexTaskCreateoffer = dslContext.selectFrom(DEX_TASK_CREATEOFFER)
						.where(DEX_TASK_CREATEOFFER.OFFERID.eq(feeRecord.getOfferid()))
						.fetchOneInto(DexTaskCreateoffer.class);

				if(dexTaskCreateoffer != null) {
					feeRecord.setTaskidCrof(dexTaskCreateoffer.getTaskid());
				} else {
					// just drop log
					// FIXME : use fee asset type, fee collector addr from crofRecord is correct implementation when pivot is not fixed. so in this case, fee tx should be failed.
					logger.warn("Cannot find matching createOfferRecord : offer#{}({})", feeRecord.getOfferid(), feeRecord.getOffertxhash());
				}

				position = "calculate_fee";

				BigDecimal feeAmount = StellarConverter.scale(feeRecord.getBoughtamount().multiply(getFeeRatePivot()));
				feeRecord.setFeeassetcode(getPivotAssetManagedInfo().getAssetCode());
				feeRecord.setFeeamount(feeAmount);
				feeRecord.setFeecollectoraddr(getPivotAssetManagedInfo().getOfferFeeHolderAddress());

				if(feeAmount.compareTo(BigDecimal.ZERO) <= 0) {
					feeRecord.setTxStatus(BctxStatusEnum.FAILED);
					feeRecord.setErrorposition(position);
					feeRecord.setErrorcode("zero_fee");
					feeRecord.setErrormessage("caclulated fee is zero.");
					feeRecord.update();
					continue;
				}

				position = "build_tx";
				StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder()
						.setMemo(feeRecord.getTaskid())
						.addSigner(new StellarSignerAccount(twService.extractKeyPair(tradeWallet)));

				sctxBuilder.addOperation(
						new PaymentOperation
								.Builder(feeRecord.getFeecollectoraddr(), getPivotAssetManagedInfo().dexAssetType(), StellarConverter.actualToString(feeAmount))
								.setSourceAccount(tradeWallet.getAccountId())
								.build()
				);

				logger.info("Collect fee for offer#{} : payment {} {} {} to {}", feeRecord.getOfferid(), feeRecord.getTradeaddr(), feeRecord.getFeeassetcode(), feeRecord.getFeeamount(), feeRecord.getFeecollectoraddr());

				position = "build_sctx";
				SubmitTransactionResponse txResponse;
				// put tx info and submit tx
				try(StellarChannelTransaction sctx = sctxBuilder.build()) {
					feeRecord.setTxSeq(sctx.getTx().getSequenceNumber());
					feeRecord.setTxHash(ByteArrayUtil.toHexString(sctx.getTx().hash()));
					feeRecord.setTxXdr(sctx.getTx().toEnvelopeXdrBase64());
					feeRecord.update();

					position = "submit_sctx";
					txResponse = sctx.submit();

					if(txResponse.isSuccess()) {
						feeRecord.setTxStatus(BctxStatusEnum.SUCCESS);
						feeRecord.update();
					} else {
						throw StellarException.from(txResponse);
					}
				} catch(IOException ioex) {
					throw new StellarException(ioex);
				}
			} catch(Exception ex) {
				TalkenException tex;
				if(ex instanceof TalkenException) tex = (TalkenException) ex;
				else tex = new GeneralException(ex);
				feeRecord.setTxStatus(BctxStatusEnum.FAILED);
				DexTaskRecord.writeError(feeRecord, position, tex);
			}
		}
	}
}
