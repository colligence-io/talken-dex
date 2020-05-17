package io.talken.dex.shared.service.blockchain.stellar;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.SingleKeyTable;
import io.talken.dex.shared.exception.SigningException;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stellar Channel transaction utility
 */
public class StellarChannelTransaction implements Closeable {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarChannelTransaction.class);

	private StellarNetworkService stellarNetworkService;
	private Server server = null;
	private Transaction tx = null;
	private StellarChannel channel = null;

	public static final long TIMEOUT = 10;

	/**
	 * use builder
	 */
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

	/**
	 * submit channel tx
	 *
	 * @return
	 * @throws IOException
	 */
	public SubmitTransactionResponse submit() throws IOException, AccountRequiresMemoException {
		try {
			// submit
			logger.debug("Sending TX {} to stellar network.", ByteArrayUtil.toHexString(tx.hash()));
			return stellarNetworkService.sendTransaction(this.server, tx);
		} finally {
			close();
		}
	}

	/**
	 * release stellar channel (locked with redis)
	 */
	@Override
	public void close() {
		stellarNetworkService.releaseChannel(this.channel);
	}

	public static class Builder {
		private StellarNetworkService stellarNetworkService;
		private String memo = null;
		private List<Operation> operations = new ArrayList<>();
		private SingleKeyTable<String, StellarSigner> signers = new SingleKeyTable<>();

		public Builder(StellarNetworkService stellarNetworkService) {
			this.stellarNetworkService = stellarNetworkService;
		}

		/**
		 * set tx memo
		 *
		 * @param memo
		 * @return
		 */
		public Builder setMemo(String memo) {
			this.memo = memo;
			return this;
		}

		/**
		 * add tx operation
		 *
		 * @param op
		 * @return
		 */
		public Builder addOperation(Operation op) {
			this.operations.add(op);
			return this;
		}

		/**
		 * add signer for transaction
		 * channel will be added automatically
		 *
		 * @param signer
		 * @return
		 */
		public Builder addSigner(StellarSigner signer) {
			this.signers.insert(signer);
			return this;
		}

		/**
		 * pick available channel, mark it as locked
		 *
		 * @return
		 * @throws IOException
		 * @throws SigningException
		 */
		public StellarChannelTransaction build() throws IOException, SigningException {
			StellarChannelTransaction sctx = new StellarChannelTransaction();
			sctx.stellarNetworkService = this.stellarNetworkService;
			try {
				sctx.server = stellarNetworkService.pickServer();
				// pick channel
				sctx.channel = stellarNetworkService.pickChannel();

				// check channel account
				AccountResponse channelAccount = sctx.server.accounts().account(sctx.channel.getAccountId());

				// update channel balance
				// XXX : it is best to update channel native balance after release, but it requires api call which is not worthy.
				sctx.channel.update(channelAccount);

				// builder
				Transaction.Builder txBuilder = new Transaction.Builder(channelAccount, stellarNetworkService.getNetwork())
						.setBaseFee(stellarNetworkService.getNetworkFee())
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
				for(StellarSigner signer : this.signers.select())
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

		/**
		 * shortcut build and submit
		 *
		 * @return
		 * @throws IOException
		 * @throws SigningException
		 */
		public SubmitTransactionResponse buildAndSubmit() throws IOException, SigningException, AccountRequiresMemoException {
			try(StellarChannelTransaction sctx = build()) {
				return sctx.submit();
			}
		}
	}
}
