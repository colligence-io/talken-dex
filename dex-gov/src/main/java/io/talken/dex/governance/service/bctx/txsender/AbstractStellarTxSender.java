package io.talken.dex.governance.service.bctx.txsender;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import com.google.gson.JsonObject;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.GSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.governance.service.bctx.TxSender;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.stellar.sdk.*;
import org.stellar.sdk.requests.ErrorResponse;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import static io.talken.dex.shared.service.blockchain.stellar.StellarChannelTransaction.TIME_BOUND;

public abstract class AbstractStellarTxSender extends TxSender {
	private final PrefixedLogger logger;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	public AbstractStellarTxSender(BlockChainPlatformEnum platform, PrefixedLogger logger) {
		super(platform);
		this.logger = logger;
	}

	/**
	 * send stellar tx to network
	 *
	 * @param asset
	 * @param bctx
	 * @param log
	 * @return
	 * @throws Exception
	 */
	protected boolean sendStellarTx(Asset asset, Bctx bctx, BctxLogRecord log) throws Exception {
		// pick horizon server
		Server server = stellarNetworkService.pickServer();

		// prepare accounts
		KeyPair source = KeyPair.fromAccountId(bctx.getAddressFrom());

		// load up-to-date information on source account.
		AccountResponse sourceAccount = server.accounts().account(source.getAccountId());

		KeyPair destination = KeyPair.fromAccountId(bctx.getAddressTo());

		boolean destinationExists = false;

		try {
			AccountResponse destinationAccount = server.accounts().account(destination.getAccountId());
			destinationExists = true;
		} catch(ErrorResponse er) {
			if(er.getCode() != 404) {
				throw er;
			}
		}

		Operation operation;
		if(destinationExists) {
			operation = new PaymentOperation
					.Builder(destination.getAccountId(), asset, StellarConverter.actualToString(bctx.getAmount()))
					.build();
		} else {
			if(!bctx.getBctxType().equals(BlockChainPlatformEnum.STELLAR))
				throw new IllegalStateException("Cannot transfer stellar token to unactivated account");
			operation = new CreateAccountOperation
					.Builder(destination.getAccountId(), StellarConverter.actualToString(bctx.getAmount()))
					.build();
		}

		Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount, stellarNetworkService.getNetwork())
                .addTimeBounds(TimeBounds.expiresAfter(TIME_BOUND))
				.setBaseFee(stellarNetworkService.getNetworkFee())
//                .setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
				.addOperation(operation);

		if(bctx.getTxAux() != null) txBuilder.addMemo(Memo.text(bctx.getTxAux()));

		// build tx
		Transaction tx = txBuilder.build();

		String txHash = ByteArrayUtil.toHexString(tx.hash());

		JsonObject requestInfo = new JsonObject();
		requestInfo.addProperty("sequence", tx.getSequenceNumber());
		requestInfo.addProperty("hash", txHash);
		requestInfo.addProperty("envelopeXdr", tx.toEnvelopeXdrBase64());

		log.setRequest(GSONWriter.toJsonString(requestInfo));

		logger.info("[BCTX#{}] Request sign for {} {}", bctx.getId(), source.getAccountId(), txHash);
		signServer().signStellarTransaction(tx);

		logger.info("[BCTX#{}] Sending TX to stellar network.", bctx.getId());

		// set bcRefId before send tx (stellar network generate txHash before submit)
		log.setBcRefId(txHash);
		SubmitTransactionResponse txResponse = stellarNetworkService.sendTransaction(server, tx);

		if(txResponse.isSuccess()) {
			// update refid with response to ensure
			log.setBcRefId(txResponse.getHash());
			log.setResponse(GSONWriter.toJsonString(txResponse));
			return true;
		} else {
			ObjectPair<String, String> resultCodes = StellarConverter.getResultCodesFromExtra(txResponse);
			log.setErrorcode(resultCodes.first());
			log.setErrormessage(resultCodes.second());
			return false;
		}
	}

    public boolean sendStellarTx(String fromAddr, String toAddr, Asset asset, Bctx bctx, BctxLogRecord log) throws Exception {
        // pick horizon server
        Server server = stellarNetworkService.pickServer();

        // prepare accounts
        KeyPair source = KeyPair.fromAccountId(bctx.getAddressFrom());

        // load up-to-date information on source account.
        AccountResponse sourceAccount = server.accounts().account(source.getAccountId());

        KeyPair destination = KeyPair.fromAccountId(bctx.getAddressTo());

        boolean destinationExists = false;

        try {
            AccountResponse destinationAccount = server.accounts().account(destination.getAccountId());
            destinationExists = true;
        } catch(ErrorResponse er) {
            if(er.getCode() != 404) {
                throw er;
            }
        }

        Operation operation;
        if(destinationExists) {
            operation = new PaymentOperation
                    .Builder(destination.getAccountId(), asset, StellarConverter.actualToString(bctx.getAmount()))
                    .build();
        } else {
            if(!bctx.getBctxType().equals(BlockChainPlatformEnum.STELLAR))
                throw new IllegalStateException("Cannot transfer stellar token to unactivated account");
            operation = new CreateAccountOperation
                    .Builder(destination.getAccountId(), StellarConverter.actualToString(bctx.getAmount()))
                    .build();
        }

        Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount, stellarNetworkService.getNetwork())
                .addTimeBounds(TimeBounds.expiresAfter(TIME_BOUND))
                .setBaseFee(stellarNetworkService.getNetworkFee())
//                .setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
                .addOperation(operation);

        if(bctx.getTxAux() != null) txBuilder.addMemo(Memo.text(bctx.getTxAux()));

        // build tx
        Transaction tx = txBuilder.build();

        String txHash = ByteArrayUtil.toHexString(tx.hash());

        JsonObject requestInfo = new JsonObject();
        requestInfo.addProperty("sequence", tx.getSequenceNumber());
        requestInfo.addProperty("hash", txHash);
        requestInfo.addProperty("envelopeXdr", tx.toEnvelopeXdrBase64());

        log.setRequest(GSONWriter.toJsonString(requestInfo));

        logger.info("[BCTX#{}] Request sign for {} {}", bctx.getId(), source.getAccountId(), txHash);
        signServer().signStellarTransaction(tx);

        logger.info("[BCTX#{}] Sending TX to stellar network.", bctx.getId());

        // set bcRefId before send tx (stellar network generate txHash before submit)
        log.setBcRefId(txHash);
        SubmitTransactionResponse txResponse = stellarNetworkService.sendTransaction(server, tx);

        if(txResponse.isSuccess()) {
            // update refid with response to ensure
            log.setBcRefId(txResponse.getHash());
            log.setResponse(GSONWriter.toJsonString(txResponse));
            return true;
        } else {
            ObjectPair<String, String> resultCodes = StellarConverter.getResultCodesFromExtra(txResponse);
            log.setErrorcode(resultCodes.first());
            log.setErrormessage(resultCodes.second());
            return false;
        }
    }
}
