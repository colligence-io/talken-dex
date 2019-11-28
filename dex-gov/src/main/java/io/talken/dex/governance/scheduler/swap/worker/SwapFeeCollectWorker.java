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
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.exception.SigningException;
import io.talken.dex.shared.service.blockchain.stellar.StellarChannelTransaction;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarSignerTSS;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.Server;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.time.Duration;

// Service Disabled [TKN-1426]
//@Component
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

		TokenMetaTable.ManagedInfo sourceMeta;
		try {
			sourceMeta = tmService.getManagedInfo(record.getSourceassetcode());
		} catch(Exception ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("get meta", ex);
		}

		long amount;
		try {
			amount = record.getSourceamountraw() - record.getPpSpentamountraw();
			if(amount <= 0) { // nothing to collect, finish
				record.setStatus(DexSwapStatusEnum.TASK_FINISHED);
				record.update();
				return new WorkerProcessResult.Builder(this, record).success();
			}

		} catch(Exception ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("build tx", ex);
		}


		// build channel tx
		StellarChannelTransaction.Builder sctxBuilder;
		try {
			sctxBuilder = stellarNetworkService.newChannelTxBuilder().setMemo(dexTaskId.getId())
					.addOperation(
							new PaymentOperation.Builder(
									record.getFcFeecollectaccount(),
									sourceMeta.dexAssetType(),
									StellarConverter.rawToActualString(amount)
							)
									.setSourceAccount(record.getSwapperaddr())
									.build()
					)
					.addSigner(new StellarSignerTSS(signServerService, record.getSwapperaddr()));
		} catch(Exception ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("build ops", ex);
		}

		SubmitTransactionResponse txResponse;
		try(StellarChannelTransaction sctx = sctxBuilder.build()) {
			// update tx info before submit
			record.setFcTaskid(dexTaskId.getId());
			record.setFcTxSeq(sctx.getTx().getSequenceNumber());
			record.setFcTxHash(ByteArrayUtil.toHexString(sctx.getTx().hash()));
			record.setFcTxEnvelopexdr(sctx.getTx().toEnvelopeXdrBase64());
			record.update();

			try {
				txResponse = sctx.submit();
			} catch(IOException ex) {
				retryOrFail(record);
				return new WorkerProcessResult.Builder(this, record).exception("submit tx", ex);
			}

		} catch(SigningException | IOException ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("build tx", ex);
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
			record.setScheduleTimestamp(null);
			record.setFinishFlag(true);
		}
		record.update();
	}
}
