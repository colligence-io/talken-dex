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
import org.stellar.sdk.PathPaymentOperation;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.xdr.OperationResult;
import org.stellar.sdk.xdr.OperationType;

import java.io.IOException;
import java.time.Duration;

// Service Disabled [TKN-1426]
//@Component
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

		TokenMetaTable.ManagedInfo sourceMeta;
		TokenMetaTable.ManagedInfo targetMeta;
		try {
			sourceMeta = tmService.getManagedInfo(record.getSourceassetcode());
			targetMeta = tmService.getManagedInfo(record.getTargetassetcode());
		} catch(Exception ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("get meta", ex);
		}

		// build channel tx
		StellarChannelTransaction.Builder sctxBuilder;
		try {
			sctxBuilder = stellarNetworkService.newChannelTxBuilder().setMemo(dexTaskId.getId())
					.addOperation(
							new PathPaymentOperation.Builder(
									sourceMeta.dexAssetType(),
									StellarConverter.rawToActualString(record.getSourceamountraw()),
									record.getSwapperaddr(),
									targetMeta.dexAssetType(),
									StellarConverter.rawToActualString(record.getTargetamountraw())
							)
									.setSourceAccount(record.getSwapperaddr())
									.build()

					).addSigner(new StellarSignerTSS(signServerService, record.getSwapperaddr()));
		} catch(Exception ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("build ops", ex);
		}

		SubmitTransactionResponse txResponse;
		try(StellarChannelTransaction sctx = sctxBuilder.build()) {
			// update tx info before submit
			record.setPpTaskid(dexTaskId.getId());
			record.setPpTxSeq(sctx.getTx().getSequenceNumber());
			record.setPpTxHash(ByteArrayUtil.toHexString(sctx.getTx().hash()));
			record.setPpTxEnvelopexdr(sctx.getTx().toEnvelopeXdrBase64());
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


		record.setPpTxResultxdr(txResponse.getResultXdr().get());
		if(txResponse.getExtras() != null)
			record.setPpTxResultextra(GSONWriter.toJsonStringSafe(txResponse.getExtras()));

		// TODO : retry if path not found...
		// op_too_few_offers -> retry
		// op_underfunded -> ??????????
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
