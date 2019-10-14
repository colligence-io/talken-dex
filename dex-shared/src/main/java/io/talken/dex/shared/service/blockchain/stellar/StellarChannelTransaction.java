package io.talken.dex.shared.service.blockchain.stellar;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.exception.SigningException;
import org.stellar.sdk.Memo;
import org.stellar.sdk.Operation;
import org.stellar.sdk.Server;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StellarChannelTransaction implements Closeable {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarChannelTransaction.class);

	private StellarNetworkService stellarNetworkService;
	private Server server = null;
	private Transaction tx = null;
	private StellarChannel channel = null;

	public static final long TIMEOUT = 30;

	private StellarChannelTransaction() { }


	public Server getServer() {
		return server;
	}

	public Transaction getTx() {
		return tx;
	}

	public StellarChannel getChannel() {
		return channel;
	}

	public SubmitTransactionResponse submit() throws IOException {
		try {
			// submit
			logger.debug("Sending TX {} to stellar network.", ByteArrayUtil.toHexString(tx.hash()));
			return this.server.submitTransaction(tx);
		} finally {
			close();
		}
	}

	@Override
	public void close() {
		stellarNetworkService.releaseChannel(this.channel);
	}

	public static class Builder {
		private StellarNetworkService stellarNetworkService;
		private String memo = null;
		private List<Operation> operations = new ArrayList<>();
		private List<StellarSigner> signers = new ArrayList<>();

		public Builder(StellarNetworkService stellarNetworkService) {
			this.stellarNetworkService = stellarNetworkService;
		}

		public Builder setMemo(String memo) {
			this.memo = memo;
			return this;
		}

		public Builder addOperation(Operation op) {
			this.operations.add(op);
			return this;
		}

		public Builder addSigner(StellarSigner signer) {
			this.signers.add(signer);
			return this;
		}

		public StellarChannelTransaction build() throws IOException, SigningException {
			StellarChannelTransaction sctx = new StellarChannelTransaction();
			sctx.stellarNetworkService = this.stellarNetworkService;
			try {
				sctx.server = stellarNetworkService.pickServer();
				// pick channel
				sctx.channel = stellarNetworkService.pickChannel();

				// check channel account
				AccountResponse channelAccount = sctx.server.accounts().account(sctx.channel.getAccountId());

				// builder
				Transaction.Builder txBuilder = new Transaction.Builder(channelAccount, stellarNetworkService.getNetwork())
						.setOperationFee(stellarNetworkService.getNetworkFee())
						.setTimeout(TIMEOUT);

				// add memo if exists
				if(this.memo != null)
					txBuilder.addMemo(Memo.text(this.memo));

				// add operations
				for(Operation operation : this.operations)
					txBuilder.addOperation(operation);

				// build tx
				Transaction tx = txBuilder.build();

				// sign tx
				for(StellarSigner signer : this.signers)
					signer.sign(tx);

				// sign tx with channel
				tx.sign(sctx.channel.getKeyPair());

				sctx.tx = tx;

				return sctx;
			} catch(Exception ex) {
				stellarNetworkService.releaseChannel(sctx.channel);
				throw ex;
			}
		}
	}
}
