package io.talken.dex.shared.service.blockchain.stellar;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.TaskIntegrityCheckFailedException;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.OperationResult;
import org.stellar.sdk.xdr.TransactionResult;
import org.stellar.sdk.xdr.XdrDataInputStream;
import shadow.com.google.common.io.BaseEncoding;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed Stellar Tx Receipt
 */
public class StellarTxReceipt {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarTxReceipt.class);

	private Network network;
	private TransactionResponse response;
	private Transaction tx;
	private TransactionResult result = null;
	private DexTaskId dexTaskId = null;
	private List<StellarOpReceipt> opReceipts = null;
	private boolean isFeeBump = false;
	private String txMemo = null;

    /**
     * Instantiates a new Stellar tx receipt.
     *
     * @param response the response
     * @param network  the network
     */
    public StellarTxReceipt(TransactionResponse response, Network network) {
		this.network = network;
		this.response = response;
		parse();
	}

	private void parse() throws StellarTxResultParsingError {
		try {
			// decode tx
			AbstractTransaction atx = Transaction.fromEnvelopeXdr(response.getEnvelopeXdr(), this.network);

			if(atx instanceof FeeBumpTransaction) {
				this.isFeeBump = true;
				this.tx = ((FeeBumpTransaction) atx).getInnerTransaction();
			} else {
				this.tx = (Transaction) atx;
			}
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
				this.txMemo = memo.toString();

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

    /**
     * Gets response.
     *
     * @return the response
     */
    public TransactionResponse getResponse() {
		return response;
	}

    /**
     * Gets transaction.
     *
     * @return the transaction
     */
    public Transaction getTransaction() {
		return this.tx;
	}

    /**
     * Gets result.
     *
     * @return the result
     */
    public TransactionResult getResult() {
		return result;
	}

    /**
     * Gets task id.
     *
     * @return the task id
     */
    public DexTaskId getTaskId() {
		return dexTaskId;
	}

    /**
     * Gets tx hash.
     *
     * @return the tx hash
     */
    public String getTxHash() {return response.getHash();}

    /**
     * Gets op receipts.
     *
     * @return the op receipts
     */
    public List<StellarOpReceipt> getOpReceipts() {
		if(opReceipts == null) {
			opReceipts = new ArrayList<>();

			Operation[] operations = this.tx.getOperations();
			OperationResult[] results;
			if(isFeeBump) {
				results = this.result.getResult().getInnerResultPair().getResult().getResult().getResults();
			} else {
				results = this.result.getResult().getResults();
			}

			for(int i = 0; i < operations.length; i++) {
				Operation op = operations[i];
				OperationResult result = results[i];

				StellarOpReceipt receipt = StellarOpReceipt.fromResponse(this.response, op, result);
				receipt.setMemo(this.txMemo);
				if(this.dexTaskId != null) receipt.setTaskId(this.dexTaskId.getId());

				opReceipts.add(receipt);
			}
		}

		return opReceipts;
	}
}
