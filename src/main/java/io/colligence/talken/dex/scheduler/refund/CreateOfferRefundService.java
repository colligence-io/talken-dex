package io.colligence.talken.dex.scheduler.refund;

import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferRefundTaskRecord;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.service.BareTxInfo;
import io.colligence.talken.dex.api.service.DexTaskId;
import io.colligence.talken.dex.api.service.DexTaskIdService;
import io.colligence.talken.dex.api.service.TokenMetaService;
import io.colligence.talken.dex.exception.SigningException;
import io.colligence.talken.dex.exception.TaskIntegrityCheckFailedException;
import io.colligence.talken.dex.exception.TokenMetaDataNotFoundException;
import io.colligence.talken.dex.service.integration.signer.SignServerService;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import io.colligence.talken.dex.util.StellarConverter;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.util.StringJoiner;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_CREATEOFFER_REFUND_TASK;

@Service
@Scope("singleton")
public class CreateOfferRefundService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateOfferRefundService.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private StellarNetworkService stellarService;

	@Autowired
	private DexTaskIdService taskIdService;

	@Autowired
	private TokenMetaService maService;

	@Autowired
	private SignServerService signerService;

	// check refund tasks
	@Scheduled(fixedDelay = 10000)
	private void do_schedule() {
		Result<DexCreateofferRefundTaskRecord> refundTasks = dslContext.selectFrom(DEX_CREATEOFFER_REFUND_TASK)
				.where(DEX_CREATEOFFER_REFUND_TASK.SUCCESS_FLAG.isNull())
				.fetch();

		for(DexCreateofferRefundTaskRecord _rt : refundTasks) {
			try {
				refund(_rt);
			} catch(Exception ex) {
				logger.exception(ex, _rt.getTaskid(), " refund failed");
				_rt.setErrorposition("Unknown");
				_rt.setErrorcode(ex.getClass().getSimpleName());
				_rt.setErrormessage(ex.getMessage());
				_rt.setSuccessFlag(false);
				_rt.update();
			}
		}
	}

	private void refund(DexCreateofferRefundTaskRecord taskRecord) {
		try {
			DexTaskId taskId = taskIdService.decode_taskId(taskRecord.getTaskid());

			Asset assetType = maService.getAssetType(taskRecord.getRefundassetcode());
			// pick horizon server
			Server server = stellarService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(taskRecord.getFeecollectaccount());

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			KeyPair destination = KeyPair.fromAccountId(taskRecord.getRefundaccount());

			Transaction tx = new Transaction
					.Builder(sourceAccount)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.addMemo(Memo.text(taskId.getId()))
					.addOperation(
							new PaymentOperation
									.Builder(destination, assetType, StellarConverter.rawToDoubleString(taskRecord.getRefundamountraw()))
									.build()
					).build();

			// build tx
			BareTxInfo bareTxInfo = BareTxInfo.build(tx);

			taskRecord.setTxSeq(bareTxInfo.getSequence());
			taskRecord.setTxHash(bareTxInfo.getHash());
			taskRecord.setTxXdr(bareTxInfo.getEnvelopeXdr());

			logger.debug("Request sign for {} {}", source.getAccountId(), taskRecord.getTxHash());
			signerService.signTransaction(tx);

			logger.debug("Sending refund TX to stellar network.");
			SubmitTransactionResponse txResponse = server.submitTransaction(tx);

			if(txResponse.isSuccess()) {
				taskRecord.setSuccessFlag(true);
				taskRecord.setTxResulthash(txResponse.getHash());
				taskRecord.setTxResultxdr(txResponse.getResultXdr());
			} else {
				SubmitTransactionResponse.Extras.ResultCodes resultCodes = txResponse.getExtras().getResultCodes();
				taskRecord.setSuccessFlag(false);
				taskRecord.setErrorposition("submit tx");
				taskRecord.setErrorcode(resultCodes.getTransactionResultCode());
				StringJoiner sj = new StringJoiner(",");
				if(resultCodes.getOperationsResultCodes() != null) resultCodes.getOperationsResultCodes().forEach(sj::add);
				taskRecord.setErrormessage(sj.toString());
			}

			taskRecord.update();
		} catch(TaskIntegrityCheckFailedException e) {
			taskRecord.setErrorposition("decoding task ID");
			taskRecord.setErrorcode(e.getClass().getSimpleName());
			taskRecord.setErrormessage(e.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();
		} catch(TokenMetaDataNotFoundException e) {
			taskRecord.setErrorposition("get asset type");
			taskRecord.setErrorcode(e.getClass().getSimpleName());
			taskRecord.setErrormessage(e.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();
		} catch(SigningException e) {
			taskRecord.setErrorposition("sign transaction");
			taskRecord.setErrorcode(e.getClass().getSimpleName());
			taskRecord.setErrormessage(e.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();
		} catch(IOException e) {
			taskRecord.setErrorposition("stellar api");
			taskRecord.setErrorcode(e.getClass().getSimpleName());
			taskRecord.setErrormessage(e.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();
		}
	}
}
