package io.talken.dex.governance.scheduler.swap.worker;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.GSONWriter;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.governance.scheduler.swap.SwapTaskWorker;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

@Component
@Scope("singleton")
@RequiredArgsConstructor
public class SwapPathPaymentWorker extends SwapTaskWorker {

	@Override
	public DexSwapStatusEnum getStartStatus() {
		// TODO : should be ANCHOR_ANCSVR_CONFIRMED
		return DexSwapStatusEnum.ANCHOR_TX_CATCH;
	}

	@Override
	public boolean proceed(DexTaskSwapRecord record) {

		// generate dexTaskId
		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.SWAP_PATHPAYMENT);
		record.setPpTaskid(dexTaskId.getId());

		// pick horizon server
		Server server = stellarNetworkService.pickServer();

		// build tx
		Transaction tx;
		try {
			// prepare accounts
			KeyPair swapper = KeyPair.fromAccountId(record.getSwapperaddr());
			KeyPair channel = getChannel();

			// load up-to-date information
			AccountResponse swapperAccount = server.accounts().account(swapper.getAccountId());
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
									swapperAccount.getAccountId(),
									targetAssetType,
									StellarConverter.rawToActualString(record.getTargetamountraw())
							)
									.setSourceAccount(swapperAccount.getAccountId())
									.build()
					);

			// build tx
			tx = txBuilder.build();

			String txHash = ByteArrayUtil.toHexString(tx.hash());

			// sign with swapper via signServer
			logger.debug("Request sign for {} {}", swapper.getAccountId(), txHash);
			signServerService.signStellarTransaction(tx, swapperAccount.getAccountId());

			// sign with channel
			tx.sign(channel);
		} catch(Exception ex) {
			updateRecordException(record, DexSwapStatusEnum.PATHPAYMENT_FAILED, "build tx", ex);
			return false;
		}

		// update tx info before submit
		record.setPpTaskid(dexTaskId.getId());
		record.setPpTxSeq(tx.getSequenceNumber());
		record.setPpTxHash(ByteArrayUtil.toHexString(tx.hash()));
		record.setPpTxEnvelopexdr(tx.toEnvelopeXdrBase64());
		record.update();

		try {
			logger.debug("Sending TX {} to stellar network.", record.getPpTxHash());
			SubmitTransactionResponse txResponse = server.submitTransaction(tx);

			// TODO : retry if path not found...
			// op_too_few_offers -> retry
			// op_underfunded -> ??????????

			record.setPpTxResultxdr(txResponse.getResultXdr().get());
			if(txResponse.getExtras() != null)
				record.setPpTxResultextra(GSONWriter.toJsonStringSafe(txResponse.getExtras()));

			if(txResponse.isSuccess()) {
				record.setStatus(DexSwapStatusEnum.PATHPAYMENT_SENT);
				record.update();
				return true;
			} else {
				ObjectPair<String, String> resultCodes = StellarConverter.getResultCodesFromExtra(txResponse);
				updateRecordError(record, DexSwapStatusEnum.PATHPAYMENT_FAILED, "submit tx", resultCodes.first(), resultCodes.second());
				return false;
			}
		} catch(Exception ex) {
			updateRecordException(record, DexSwapStatusEnum.PATHPAYMENT_FAILED, "submit tx", ex);
			return false;
		}
	}
}
