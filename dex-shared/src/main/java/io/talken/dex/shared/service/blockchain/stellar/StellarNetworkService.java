package io.talken.dex.shared.service.blockchain.stellar;


import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Network;
import org.stellar.sdk.Server;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.responses.SubmitTransactionTimeoutResponseException;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.talken.common.persistence.redis.RedisConsts.KEY_GOVERNANCE_DEX_CHANNEL;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class StellarNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarNetworkService.class);

	@Autowired
	private StringRedisTemplate redisTemplate;

	private final DexSettings dexSettings;

	private String serverUri;

	private String publicServerUri;

	private static final int BASE_FEE = 100;

	private static final int PICK_CHANNEL_TMIEOUT = 10000;

	private Network network;

	private final List<StellarChannel> channels = new ArrayList<>();

	private static final int MAXIMUM_SUBMIT_RETRY = 5;

	@PostConstruct
	private void init() throws IOException {
		this.network = dexSettings.getBcnode().getStellar().getNetwork().equalsIgnoreCase("test") ? Network.TESTNET : Network.PUBLIC;
		this.serverUri = dexSettings.getBcnode().getStellar().getRpcUri();
		this.publicServerUri = dexSettings.getBcnode().getStellar().getPublicRpcUri();

		logger.info("Using Stellar {} Network : {}", this.network, this.serverUri);
		logger.info("Using Stellar Public {} Network : {}", this.network, this.publicServerUri);

		for(Map.Entry<String, String> _ch : dexSettings.getBcnode().getStellar().getSecret().getChannels().entrySet()) {
			KeyPair chkp = KeyPair.fromSecretSeed(_ch.getValue());
			if(!chkp.getAccountId().equals(_ch.getKey()))
				throw new IllegalArgumentException("Stellar Channel for " + _ch.getKey() + " has set with mismatch secretKey.");
			final StellarChannel sc = new StellarChannel(chkp);
			AccountResponse accountResponse = pickServer().accounts().account(chkp.getAccountId());
			sc.update(accountResponse);
			channels.add(sc);
			logger.info("Stellar Channel {} added : {} XLM", sc.getAccountId(), sc.getBalance().stripTrailingZeros().toPlainString());
		}
	}

	public Server pickServer() {
		return new Server(this.serverUri);
	}

	public Server pickPublicServer() {
		return new Server(this.publicServerUri);
	}

	public Network getNetwork() {
		return this.network;
	}

	public int getNetworkFee() {
		return BASE_FEE;
	}

	/**
	 * shortcut new channel tx builder
	 *
	 * @return
	 */
	public StellarChannelTransaction.Builder newChannelTxBuilder() {
		return new StellarChannelTransaction.Builder(this);
	}

	/**
	 * pick available channel
	 *
	 * @return
	 */
	public StellarChannel pickChannel() {
		synchronized(channels) {
			Collections.sort(channels);
			long until = System.currentTimeMillis() + PICK_CHANNEL_TMIEOUT;
			while(System.currentTimeMillis() < until) {
				for(StellarChannel channel : channels) {
					// set redis as channel is picked, and set expiration as 31 seconds
					Boolean set = redisTemplate.opsForValue().setIfAbsent(KEY_GOVERNANCE_DEX_CHANNEL + ":" + channel.getAccountId(), "1", Duration.ofSeconds(StellarChannelTransaction.TIMEOUT + 1));
					if(set != null && set) {
						logger.debug("Channel pick : {}", channel.getAccountId());
						return channel;
					}
				}
				try {
					Thread.sleep(100); // wait 100 ms before try to pick channel again
				} catch(InterruptedException ex) {
					return null;
				}
			}
		}
		return null;
	}

	/**
	 * release channel
	 *
	 * @param channelAccount
	 */
	public void releaseChannel(StellarChannel channelAccount) {
		if(channelAccount != null) {
			Boolean released = redisTemplate.delete(KEY_GOVERNANCE_DEX_CHANNEL + ":" + channelAccount.getAccountId());
			if(released != null && released)
				logger.debug("Channel release : {}", channelAccount.getAccountId());
		}
	}

	/**
	 * get all channel list
	 *
	 * @return
	 */
	public List<String> getChannelList() {
		return channels.stream().map(StellarChannel::getAccountId).collect(Collectors.toList());
	}

	/**
	 * Send Stellar Transaction to network, if timeout occurred, retry MAXIMUM_SUBMIT_RETRY times
	 *
	 * @param server
	 * @param tx
	 * @return
	 * @throws IOException
	 */
	public SubmitTransactionResponse sendTransaction(Server server, Transaction tx) throws IOException {
		SubmitTransactionTimeoutResponseException rtn = null;
		for(int i = 0; i < MAXIMUM_SUBMIT_RETRY; i++) {
			try {
				return server.submitTransaction(tx);
			} catch(Exception ex) {
				if(ex instanceof SubmitTransactionTimeoutResponseException) {
					rtn = (SubmitTransactionTimeoutResponseException) ex;
					logger.warn("Stellar TX submit timeout occured, trial = {}", i + 1);
				} else {
					throw ex;
				}
			}
		}
		throw rtn;
	}
}
