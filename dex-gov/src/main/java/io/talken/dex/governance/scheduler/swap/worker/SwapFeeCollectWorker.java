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

import java.io.IOException;
import java.time.Duration;

@Component
@Scope("singleton")
@RequiredArgsConstructor
public class SwapFeeCollectWorker extends SwapTaskWorker {
	@Override
	protected int getRetryCount() {
		return 10;
	}

	@Override
	protected Duration getRetryInterval() {
		return Duration.ofMinutes(1);
	}

	@Override
	public DexSwapStatusEnum getStartStatus() {
		return DexSwapStatusEnum.FEECOLLECT_QUEUED;
	}

	@Override
	public WorkerProcessResult proceed(DexTaskSwapRecord record) {

		// generate dexTaskId
		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.SWAP_FEECOLLECT);
		record.setFcTaskid(dexTaskId.getId());

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
			Long amount = record.getSourceamountraw() - record.getPpSpentamountraw();
			if(amount <= 0) { // nothing to collect, finish
				record.setStatus(DexSwapStatusEnum.TASK_FINISHED);
				record.update();
				return new WorkerProcessResult.Builder(this, record).success();
			}

			Transaction.Builder txBuilder = new Transaction.Builder(channelAccount, stellarNetworkService.getNetwork())
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.setOperationFee(stellarNetworkService.getNetworkFee())
					.addMemo(Memo.text(dexTaskId.getId()))
					.addOperation(
							new PaymentOperation.Builder(
									record.getFcFeecollectaccount(),
									sourceAssetType,
									StellarConverter.rawToActualString(amount)
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
		record.setFcTaskid(dexTaskId.getId());
		record.setFcTxSeq(tx.getSequenceNumber());
		record.setFcTxHash(ByteArrayUtil.toHexString(tx.hash()));
		record.setFcTxEnvelopexdr(tx.toEnvelopeXdrBase64());
		record.update();

		SubmitTransactionResponse txResponse;
		try {
			logger.debug("Sending TX {} to stellar network.", record.getFcTxHash());
			txResponse = server.submitTransaction(tx);
		} catch(IOException ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("submit tx", ex);
		}

		record.setFcTxResultxdr(txResponse.getResultXdr().get());
		if(txResponse.getExtras() != null)
			record.setFcTxResultextra(GSONWriter.toJsonStringSafe(txResponse.getExtras()));

		if(!txResponse.isSuccess()) {
			retryOrFail(record);
			ObjectPair<String, String> resultCodes = StellarConverter.getResultCodesFromExtra(txResponse);
			return new WorkerProcessResult.Builder(this, record).error("submit tx", resultCodes.first(), resultCodes.second());
		}

		record.setStatus(DexSwapStatusEnum.FEECOLLECT_SENT);
		record.update();

		return new WorkerProcessResult.Builder(this, record).success();
	}

	private void retryOrFail(DexTaskSwapRecord record) {
		if(record.getFcRetryCount() < getRetryCount()) {
			record.setStatus(DexSwapStatusEnum.FEECOLLECT_QUEUED);
			record.setFcRetryCount(record.getFcRetryCount() + 1);
			record.setScheduleTimestamp(UTCUtil.getNow().plus(getRetryInterval()));
		} else {
			record.setStatus(DexSwapStatusEnum.FEECOLLECT_FAILED);
			record.setFinishFlag(true);
		}
		record.update();
	}
}
