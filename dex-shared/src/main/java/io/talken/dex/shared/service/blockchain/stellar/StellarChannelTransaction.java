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

    /**
     * The constant TIMEOUT.
     */
    public static final long TIMEOUT = 30;
    /**
     * The constant TIME_BOUND.
     */
    public static final long TIME_BOUND = 30;

	/**
	 * use builder
	 */
	private StellarChannelTransaction() { }


    /**
     * Gets server.
     *
     * @return the server
     */
    public Server getServer() {
		return server;
	}

    /**
     * Gets tx.
     *
     * @return the tx
     */
    public Transaction getTx() {
		return tx;
	}

    /**
     * Gets channel.
     *
     * @return the channel
     */
    public StellarChannel getChannel() {
		return channel;
	}

    /**
     * submit channel tx
     *
     * @return submit transaction response
     * @throws IOException                  the io exception
     * @throws AccountRequiresMemoException the account requires memo exception
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

    /**
     * The type Builder.
     */
    public static class Builder {
		private StellarNetworkService stellarNetworkService;
		private String memo = null;
		private List<Operation> operations = new ArrayList<>();
		private SingleKeyTable<String, StellarSigner> signers = new SingleKeyTable<>();

        /**
         * Instantiates a new Builder.
         *
         * @param stellarNetworkService the stellar network service
         */
        public Builder(StellarNetworkService stellarNetworkService) {
			this.stellarNetworkService = stellarNetworkService;
		}

        /**
         * set tx memo
         *
         * @param memo the memo
         * @return memo
         */
        public Builder setMemo(String memo) {
			this.memo = memo;
			return this;
		}

        /**
         * add tx operation
         *
         * @param op the op
         * @return builder
         */
        public Builder addOperation(Operation op) {
			this.operations.add(op);
			return this;
		}

        /**
         * add signer for transaction
         * channel will be added automatically
         *
         * @param signer the signer
         * @return builder
         */
        public Builder addSigner(StellarSigner signer) {
			this.signers.insert(signer);
			return this;
		}

        /**
         * pick available channel, mark it as locked
         *
         * @return stellar channel transaction
         * @throws IOException      the io exception
         * @throws SigningException the signing exception
         */
        public StellarChannelTransaction build() throws IOException, SigningException {
			StellarChannelTransaction sctx = new StellarChannelTransaction();
			sctx.stellarNetworkService = this.stellarNetworkService;
			try {
				sctx.server = stellarNetworkService.pickServer();
//                sctx.server = stellarNetworkService.pickPublicServer();
				// pick channel
				sctx.channel = stellarNetworkService.pickChannel();

				// check channel account
				AccountResponse channelAccount = sctx.server.accounts().account(sctx.channel.getAccountId());

				// update channel balance
				// XXX : it is best to update channel native balance after release, but it requires api call which is not worthy.
				sctx.channel.update(channelAccount);

				// builder
				Transaction.Builder txBuilder = new Transaction.Builder(channelAccount, stellarNetworkService.getNetwork())
                        .addTimeBounds(TimeBounds.expiresAfter(TIME_BOUND));
//						.setTimeout(Transaction.Builder.TIMEOUT_INFINITE);

				// add memo if exists
				if(this.memo != null)
					txBuilder.addMemo(Memo.text(this.memo));

				// add operations
				for(Operation operation : this.operations)
					txBuilder.addOperation(operation);

				// Fee Calc
                txBuilder.setBaseFee(stellarNetworkService.getNetworkFee() * this.operations.size());

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
         * @return submit transaction response
         * @throws IOException                  the io exception
         * @throws SigningException             the signing exception
         * @throws AccountRequiresMemoException the account requires memo exception
         */
        public SubmitTransactionResponse buildAndSubmit() throws IOException, SigningException, AccountRequiresMemoException {
			try(StellarChannelTransaction sctx = build()) {
				return sctx.submit();
			}
		}
	}
}
