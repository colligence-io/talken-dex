package io.talken.dex.shared.service.blockchain.stellar;

import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.TaskIntegrityCheckFailedException;
import org.stellar.sdk.Memo;
import org.stellar.sdk.Operation;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.*;
import shadow.com.google.common.io.BaseEncoding;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class StellarTxReceipt {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarSignerTSS.class);

	private Network network;
	private TransactionResponse response;
	private Transaction tx;
	private TransactionResult result = null;
	private DexTaskId dexTaskId = null;
	private List<StellarTransferReceipt> paymentReceipts = null;
	private Long offerId = -1L; // -1 means no offerId
	private String txMemo = null;

	public StellarTxReceipt(TransactionResponse response, Network network) {
		this.network = network;
		this.response = response;
		parse();
	}

	private void parse() throws StellarTxResultParsingError {
		try {
			// decode tx
			this.tx = Transaction.fromEnvelopeXdr(response.getEnvelopeXdr(), this.network);
		} catch(Exception ex) {
			throw new StellarTxResultParsingError("EnvelopeDecodeError", ex);
		}

		try {
			// decode result
			byte[] bytes = BaseEncoding.base64().decode(response.getResultXdr());
			ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
			XdrDataInputStream xdrInputStream = new XdrDataInputStream(inputStream);
			this.result = TransactionResult.decode(xdrInputStream);
		} catch(Exception ex) {
			throw new StellarTxResultParsingError("ResultDecodeError", ex);
		}

		try {
			Memo memo = response.getMemo();
			if(memo instanceof MemoText) {
				this.txMemo = ((MemoText) memo).getText();
				if(this.txMemo.startsWith("TALKEN")) {
					try {
						dexTaskId = DexTaskId.decode_taskId(this.txMemo);
					} catch(TaskIntegrityCheckFailedException e) {
						logger.warn("Invalid DexTaskId [{}] detected : txHash = {}", this.txMemo, response.getHash());
					}
				}
			}
		} catch(Exception ex) {
			logger.exception(ex, "Exception while extract dexTaskId from {}", response.getHash());
		}
	}

	public TransactionResponse getResponse() {
		return response;
	}

	public Transaction getTransaction() {
		return this.tx;
	}

	public TransactionResult getResult() {
		return result;
	}

	public DexTaskId getTaskId() {
		return dexTaskId;
	}

	public String getTxHash() {return response.getHash();}

	public synchronized List<StellarTransferReceipt> getPaymentReceipts() {
		if(paymentReceipts == null) {
			paymentReceipts = new ArrayList<>();

			if(this.tx.getOperations() != null) {
				for(Operation _op : tx.getOperations()) {
					if(_op instanceof PaymentOperation) {
						PaymentOperation operation = (PaymentOperation) _op;

						StellarTransferReceipt rcpt = new StellarTransferReceipt();
						rcpt.setLedger(this.response.getLedger());
						rcpt.setPagingToken(this.response.getPagingToken());
						rcpt.setHash(this.response.getHash());
						rcpt.setSourceAccount(this.response.getSourceAccount());
						if(this.response.getMaxFee() != null) rcpt.setFeeMax(BigInteger.valueOf(this.response.getMaxFee()));
						rcpt.setFeeCharged(BigInteger.valueOf(this.response.getFeeCharged()));
						rcpt.setSeq(this.response.getSourceAccountSequence());
						rcpt.setAssetType(operation.getAsset().getType());
						rcpt.setTimeStamp(UTCUtil.toTimestamp_s(StellarConverter.toLocalDateTime(this.response.getCreatedAt())));
						rcpt.setTxMemo(this.txMemo);

						if(operation.getAsset().getType().equalsIgnoreCase("native")) {
							rcpt.setTokenSymbol("XLM");
						} else {
							AssetTypeCreditAlphaNum asset = ((AssetTypeCreditAlphaNum) operation.getAsset());
							rcpt.setTokenSymbol(asset.getCode());
							rcpt.setTokenIssuer(asset.getIssuer());
						}

						rcpt.setFrom((operation.getSourceAccount() != null) ? operation.getSourceAccount() : tx.getSourceAccount());
						rcpt.setAmountRaw(StellarConverter.actualToRaw(new BigDecimal(operation.getAmount())));
						rcpt.setTo(operation.getDestination());

						paymentReceipts.add(rcpt);
					}
				}
			}
		}
		return paymentReceipts;
	}

	public Long getOfferIdFromResult() {
		if(offerId == -1) {
			offerId = extractOfferId();
		}
		return offerId;
	}

	private Long extractOfferId() {
		if(result != null) {
			if(result.getResult() == null) return null;
			if(result.getResult().getResults() == null || result.getResult().getResults().length == 0) return null;

			// extract feeResult and offerResult
			ManageSellOfferResult offerResult = null;
			for(OperationResult operationResult : result.getResult().getResults()) {
				if(operationResult.getTr().getDiscriminant() == OperationType.MANAGE_SELL_OFFER) {
					offerResult = operationResult.getTr().getManageSellOfferResult();
				}
			}
			if(offerResult == null) return null;

			if(offerResult.getSuccess() == null) return null;
			if(offerResult.getSuccess().getOffer() == null) return null;
			if(offerResult.getSuccess().getOffer().getOffer() == null) return null;

			return offerResult.getSuccess().getOffer().getOffer().getOfferID().getInt64();

		} else return null;
	}
}
