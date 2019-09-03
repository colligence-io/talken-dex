package io.talken.dex.governance.scheduler.swap.worker;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.GSONWriter;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.governance.scheduler.swap.SwapTaskWorker;
import io.talken.dex.governance.scheduler.swap.WorkerProcessResult;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.xdr.OperationResult;
import org.stellar.sdk.xdr.OperationType;

import java.io.IOException;
import java.time.Duration;

@Component
@Scope("singleton")
@RequiredArgsConstructor
public class SwapPathPaymentWorker extends SwapTaskWorker {
	@Override
	protected int getRetryCount() {
		return 5;
	}

	@Override
	protected Duration getRetryInterval() {
		return Duration.ofMinutes(1);
	}

	@Override
	public DexSwapStatusEnum getStartStatus() {
		return DexSwapStatusEnum.PATHPAYMENT_QUEUED;
	}

	@Override
	public WorkerProcessResult proceed(DexTaskSwapRecord record) {

		// generate dexTaskId
		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.SWAP_PATHPAYMENT);
		record.setPpTaskid(dexTaskId.getId());

		// pick horizon server
		Server server = stellarNetworkService.pickServer();

		// build tx
		Transaction tx;
		try {
			// prepare accounts
			KeyPair channel = getChannel();

			// load up-to-date information
			AccountResponse channelAccount = server.accounts().account(channel.getAccountId());

			// get assetType
			Asset sourceAssetType = tmService.getManaged(record.getSourceassetcode()).getAssetType();
			Asset targetAssetType = tmService.getManaged(record.getTargetassetcode()).getAssetType();

			Transaction.Builder txBuilder = new Transaction.Builder(channelAccount, stellarNetworkService.getNetwork())
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.setOperationFee(stellarNetworkService.getNetworkFee())
					.addMemo(Memo.text(dexTaskId.getId()))
					.addOperation(
							new PathPaymentOperation.Builder(
									sourceAssetType,
									StellarConverter.rawToActualString(record.getSourceamountraw()),
									record.getSwapperaddr(),
									targetAssetType,
									StellarConverter.rawToActualString(record.getTargetamountraw())
							)
									.setSourceAccount(record.getSwapperaddr())
									.build()
					);

			// build tx
			tx = txBuilder.build();

			String txHash = ByteArrayUtil.toHexString(tx.hash());

			// sign with swapper via signServer
			logger.debug("Request sign for {} {}", record.getSwapperaddr(), txHash);
			signServerService.signStellarTransaction(tx, record.getSwapperaddr());

			// sign with channel
			tx.sign(channel);
		} catch(Exception ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("build tx", ex);
		}

		// update tx info before submit
		record.setPpTaskid(dexTaskId.getId());
		record.setPpTxSeq(tx.getSequenceNumber());
		record.setPpTxHash(ByteArrayUtil.toHexString(tx.hash()));
		record.setPpTxEnvelopexdr(tx.toEnvelopeXdrBase64());
		record.update();

		SubmitTransactionResponse txResponse;
		try {
			logger.debug("Sending TX {} to stellar network.", record.getPpTxHash());
			txResponse = server.submitTransaction(tx);
		} catch(IOException ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("submit tx", ex);
		}

		// TODO : retry if path not found...
		// op_too_few_offers -> retry
		// op_underfunded -> ??????????

		record.setPpTxResultxdr(txResponse.getResultXdr().get());
		if(txResponse.getExtras() != null)
			record.setPpTxResultextra(GSONWriter.toJsonStringSafe(txResponse.getExtras()));

		if(!txResponse.isSuccess()) {
			retryOrFail(record);
			ObjectPair<String, String> resultCodes = StellarConverter.getResultCodesFromExtra(txResponse);
			return new WorkerProcessResult.Builder(this, record).error("submit tx", resultCodes.first(), resultCodes.second());
		}

		Long spentRaw = null;
		try {
			for(OperationResult operationResult : txResponse.getDecodedTransactionResult().get().getResult().getResults()) {
				if(operationResult.getTr().getDiscriminant() == OperationType.PATH_PAYMENT) {
					spentRaw = operationResult.getTr().getPathPaymentResult().getSuccess().getOffers()[0].getAmountBought().getInt64();
				}
			}
		} catch(Exception ex) {
			// DO NOT RETRY
			record.setStatus(DexSwapStatusEnum.TASK_HALTED);
			record.update();
			return new WorkerProcessResult.Builder(this, record).exception("extract spent amount", ex);
		}

		if(spentRaw == null) {
			// DO NOT RETRY
			record.setStatus(DexSwapStatusEnum.TASK_HALTED);
			record.update();
			return new WorkerProcessResult.Builder(this, record).error("extract spent amount", "inplementation", "Cannot find path payment result");
		}

		record.setPpSpentamountraw(spentRaw);
		record.setStatus(DexSwapStatusEnum.PATHPAYMENT_SENT);
		record.update();

		return new WorkerProcessResult.Builder(this, record).success();
	}

	private void retryOrFail(DexTaskSwapRecord record) {
		if(record.getPpRetryCount() < getRetryCount()) {
			record.setStatus(DexSwapStatusEnum.PATHPAYMENT_QUEUED);
			record.setPpRetryCount(record.getPpRetryCount() + 1);
			record.setScheduleTimestamp(UTCUtil.getNow().plus(getRetryInterval()));
		} else {
			record.setStatus(DexSwapStatusEnum.PATHPAYMENT_FAILED);
			record.setScheduleTimestamp(null);
		}
		record.update();
	}
}
