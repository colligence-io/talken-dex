package io.talken.dex.governance.scheduler.swap.worker;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.GSONWriter;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.dex.governance.scheduler.swap.SwapTaskWorker;
import io.talken.dex.governance.scheduler.swap.WorkerProcessResult;
import io.talken.dex.governance.service.TokenMeta;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.integration.anchor.AncServerDeanchorRequest;
import io.talken.dex.shared.service.integration.anchor.AncServerDeanchorResponse;
import io.talken.dex.shared.service.integration.anchor.AnchorServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.stellar.sdk.Memo;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.Server;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.time.Duration;

@Component
@Scope("singleton")
@RequiredArgsConstructor
public class SwapDeanchorWorker extends SwapTaskWorker {
	private final AnchorServerService anchorServerService;

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
		return DexSwapStatusEnum.DEANCHOR_QUEUED;
	}

	@Override
	public WorkerProcessResult proceed(DexTaskSwapRecord record) {
		// generate dexTaskId
		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.SWAP_DEANCHOR);
		record.setDeancTaskid(dexTaskId.getId());

		// pick horizon server
		Server server = stellarNetworkService.pickServer();

		TokenMeta.ManagedInfo targetMeta;
		try {
			targetMeta = tmService.getManaged(record.getTargetassetcode());
		} catch(Exception ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("get meta", ex);
		}

		// build tx with channel
		Transaction tx;
		try {
			tx = stellarNetworkService.buildTxWithChannel(server, (txBuilder) -> {
				Transaction _tx = txBuilder
						.setOperationFee(stellarNetworkService.getNetworkFee())
						.addMemo(Memo.text(dexTaskId.getId()))
						.addOperation(
								new PaymentOperation.Builder(
										targetMeta.getBaseaddress(),
										targetMeta.getAssetType(),
										StellarConverter.rawToActualString(record.getTargetamountraw())
								)
										.setSourceAccount(record.getSwapperaddr())
										.build()
						).build();

				String txHash = ByteArrayUtil.toHexString(_tx.hash());

				// sign with swapper via signServer
				logger.debug("Request sign for {} {}", record.getSwapperaddr(), txHash);
				signServerService.signStellarTransaction(_tx, record.getSwapperaddr());

				return _tx;
			});
		} catch(Exception ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("build tx", ex);
		}
//
//		// build tx
//		Transaction tx;
//		try {
//			// prepare accounts
//			KeyPair channel = getChannel();
//
//			// load up-to-date information
//			AccountResponse channelAccount = server.accounts().account(channel.getAccountId());
//
//			// get assetType
//			Asset assetType = tmService.getManaged(record.getTargetassetcode()).getAssetType();
//			assetBaseAddr = tmService.getManaged(record.getTargetassetcode()).getBaseaddress();
//
//
//			Transaction.Builder txBuilder = new Transaction.Builder(channelAccount, stellarNetworkService.getNetwork())
//					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
//					.setOperationFee(stellarNetworkService.getNetworkFee())
//					.addMemo(Memo.text(dexTaskId.getId()))
//					.addOperation(
//							new PaymentOperation.Builder(
//									assetBaseAddr,
//									assetType,
//									StellarConverter.rawToActualString(record.getTargetamountraw())
//							)
//									.setSourceAccount(record.getSwapperaddr())
//									.build()
//					);
//
//			// build tx
//			tx = txBuilder.build();
//
//			String txHash = ByteArrayUtil.toHexString(tx.hash());
//
//			// sign with swapper via signServer
//			logger.debug("Request sign for {} {}", record.getSwapperaddr(), txHash);
//			signServerService.signStellarTransaction(tx, record.getSwapperaddr());
//
//			// sign with channel
//			tx.sign(channel);
//		} catch(Exception ex) {
//			retryOrFail(record);
//			return new WorkerProcessResult.Builder(this, record).exception("build tx", ex);
//		}

		// request deanchor monitor
		AncServerDeanchorRequest deanchor_request = new AncServerDeanchorRequest();
		deanchor_request.setTaskId(dexTaskId.getId());
		deanchor_request.setUid(String.valueOf(record.getUserId()));
		deanchor_request.setSymbol(record.getTargetassetcode());
		deanchor_request.setHash(ByteArrayUtil.toHexString(tx.hash()));
		deanchor_request.setFrom(record.getSwapperaddr());
		deanchor_request.setTo(targetMeta.getBaseaddress());
		deanchor_request.setAddress(record.getPrivatetargetaddr());
		deanchor_request.setValue(StellarConverter.rawToActual(record.getTargetamountraw()).doubleValue());
		deanchor_request.setMemo(UTCUtil.getNow().toString());

		IntegrationResult<AncServerDeanchorResponse> deanchorResult = anchorServerService.requestDeanchor(deanchor_request);

		if(!deanchorResult.isSuccess()) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).error("request deanchor", deanchorResult.getErrorCode(), deanchorResult.getErrorMessage());
		}

		// update tx info before submit
		record.setRefundFlag(false);
		record.setStatus(DexSwapStatusEnum.DEANCHOR_REQUESTED);
		record.setDeancTaskid(dexTaskId.getId());
		record.setDeancTxSeq(tx.getSequenceNumber());
		record.setDeancTxHash(ByteArrayUtil.toHexString(tx.hash()));
		record.setDeancTxEnvelopexdr(tx.toEnvelopeXdrBase64());
		record.setDeancIndex(deanchorResult.getData().getData().getIndex());
		record.update();

		SubmitTransactionResponse txResponse;
		try {
			logger.debug("Sending TX {} to stellar network.", record.getDeancTxHash());
			txResponse = server.submitTransaction(tx);
		} catch(IOException ex) {
			retryOrFail(record);
			return new WorkerProcessResult.Builder(this, record).exception("submit tx", ex);
		}

		record.setDeancTxResultxdr(txResponse.getResultXdr().get());
		if(txResponse.getExtras() != null)
			record.setDeancTxResultextra(GSONWriter.toJsonStringSafe(txResponse.getExtras()));

		if(!txResponse.isSuccess()) {
			retryOrFail(record);
			ObjectPair<String, String> resultCodes = StellarConverter.getResultCodesFromExtra(txResponse);
			return new WorkerProcessResult.Builder(this, record).error("tx response", resultCodes.first(), resultCodes.second());
		}

		record.setStatus(DexSwapStatusEnum.DEANCHOR_SENT);
		record.update();
		return new WorkerProcessResult.Builder(this, record).success();
	}

	private void retryOrFail(DexTaskSwapRecord record) {
		if(record.getDeancRetryCount() < getRetryCount()) {
			record.setStatus(DexSwapStatusEnum.DEANCHOR_QUEUED);
			record.setDeancRetryCount(record.getDeancRetryCount() + 1);
			record.setScheduleTimestamp(UTCUtil.getNow().plus(getRetryInterval()));
		} else {
			record.setStatus(DexSwapStatusEnum.DEANCHOR_FAILED);
			record.setScheduleTimestamp(null);
			record.setFinishFlag(true);
		}
		record.update();
	}
}
