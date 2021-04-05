package io.talken.dex.shared.service.blockchain.stellar;

import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.shared.DexSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.FeeStatsResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.responses.SubmitTransactionTimeoutResponseException;
import shadow.okhttp3.OkHttpClient;
import shadow.okhttp3.Request;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static io.talken.common.persistence.redis.RedisConsts.KEY_GOVERNANCE_DEX_CHANNEL;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class StellarNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarNetworkService.class);

	// BEANS
    private final Environment env;
    private final BuildProperties buildProperties;
	private final StringRedisTemplate redisTemplate;
    private final AdminAlarmService alarmService;
	private final DexSettings dexSettings;

	private String serverUri;

	private String publicServerUri;

	private static final int BASE_FEE = 100001;
    private static final int MAX_BASE_FEE = 100001;

	private static final int PICK_CHANNEL_TIMEOUT = 10000;

	private Network network;

	private final List<StellarChannel> channels = new ArrayList<>();

	private static final int MAXIMUM_SUBMIT_RETRY = 5;

    private String appName;
    private String appVersion;

//    @Autowired
//    private SignServerService signServerService;

	@PostConstruct
	private void init() throws IOException {
		this.network = dexSettings.getBcnode().getStellar().getNetwork().equalsIgnoreCase("test") ? Network.TESTNET : Network.PUBLIC;
		this.serverUri = dexSettings.getBcnode().getStellar().getRpcUri();
		this.publicServerUri = dexSettings.getBcnode().getStellar().getPublicRpcUri();
        this.appName = env.getProperty("spring.application.name");
        this.appVersion = buildProperties.getVersion();

		logger.info("Using Stellar {} Network : {}", this.network, this.serverUri);
		logger.info("Using Stellar Public {} Network : {}", this.network, this.publicServerUri);

		for(Map.Entry<String, String> _ch : dexSettings.getBcnode().getStellar().getSecret().getChannels().entrySet()) {
			KeyPair chkp = KeyPair.fromSecretSeed(_ch.getValue());
			if(!chkp.getAccountId().equals(_ch.getKey()))
				throw new IllegalArgumentException("Stellar Channel for " + _ch.getKey() + " has set with mismatch secretKey.");
			final StellarChannel sc = new StellarChannel(chkp);
			AccountResponse accountResponse = pickPublicServer().accounts().account(chkp.getAccountId());
			sc.update(accountResponse);
			channels.add(sc);
			logger.info("Stellar Channel {} added : {} XLM", sc.getAccountId(), sc.getBalance().stripTrailingZeros().toPlainString());
		}
	}

	public Server pickServer() {
	    if (this.appName != null && this.appVersion != null)
            return pickServer(this.appName, this.appVersion);
		return new Server(this.serverUri);
	}

    public Server pickServer(String appName, String appVersion) {
        Server server = new Server(this.serverUri);
        return getServer(appName, appVersion, server);
    }

    public Server pickPublicServer() {
        if (this.appName != null && this.appVersion != null)
            return pickPublicServer(this.appName, this.appVersion);
		return new Server(this.publicServerUri);
	}

    public Server pickPublicServer(String appName, String appVersion) {
        Server server = new Server(this.publicServerUri);
        return getServer(appName, appVersion, server);
    }

    private Server getServer(String appName, String appVersion, Server server) {
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            Request request = original.newBuilder()
                    .header("X-App-Name", appName)
                    .header("X-App-Version", appVersion)
                    .method(original.method(), original.body())
                    .build();
            return chain.proceed(request);
        });

        OkHttpClient client = httpClient.build();

        server.setHttpClient(client);
        server.setSubmitHttpClient(client);

        return server;
    }

	public Network getNetwork() {
		return this.network;
	}

	public int getNetworkFee() {
		return BASE_FEE;
	}

    public int getNetworkMaxFee() {
        return MAX_BASE_FEE;
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
			long until = System.currentTimeMillis() + PICK_CHANNEL_TIMEOUT;
			while(System.currentTimeMillis() < until) {
				for(StellarChannel channel : channels) {
					// set redis as channel is picked, and set expiration as 31 seconds
					String uuid = UUID.randomUUID().toString();
					String redisKey = KEY_GOVERNANCE_DEX_CHANNEL + ":" + channel.getAccountId();
					Boolean set = redisTemplate.opsForValue().setIfAbsent(KEY_GOVERNANCE_DEX_CHANNEL + ":" + channel.getAccountId(), uuid, Duration.ofSeconds(StellarChannelTransaction.TIME_BOUND + 1));
					if(set != null && set) {
						String check = redisTemplate.opsForValue().get(redisKey);
						if(check != null && check.equals(uuid)) {
							logger.debug("Channel pick : {}", channel.getAccountId());
							return channel;
						}
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

	public int getBaseFeeFromServer() throws IOException {
        Server server = this.pickPublicServer();
        FeeStatsResponse resp = server.feeStats().execute();
        return resp.getLastLedgerBaseFee().intValue();
    }

    public int getMaxFeeFromServer() throws IOException {
        Server server = this.pickPublicServer();
        FeeStatsResponse resp = server.feeStats().execute();
        return resp.getMaxFee().getMax().intValue();
    }

	/**
	 * Send Stellar Transaction to network, if timeout occurred, retry MAXIMUM_SUBMIT_RETRY times
	 *
	 * @param server
	 * @param tx
	 * @return
	 * @throws IOException
	 */
	public SubmitTransactionResponse sendTransaction(Server server, Transaction tx) throws IOException, AccountRequiresMemoException {
//        AccountResponse sourceAccount = null;
		SubmitTransactionTimeoutResponseException rtn = null;
		for(int i = 0; i < MAXIMUM_SUBMIT_RETRY; i++) {
			try {
			    // TODO : rebuild tx set more fee
			    if (i > 0) {
			        // TODO : add fee and build tx
//                    if (sourceAccount == null)
//                        sourceAccount = server.accounts().account(tx.getSourceAccount());
                    int baseFee = BASE_FEE;

                    if (i < 4) {
                        baseFee = getBaseFeeFromServer();
                    } else {
                        int maxBaseFee = getMaxFeeFromServer();
                        if (maxBaseFee > MAX_BASE_FEE) baseFee = maxBaseFee;
                        else baseFee = MAX_BASE_FEE;
                    }
                    logger.warn("[TEST] Re-CalculationBase Fee = {}, {}", i + 1, baseFee);

//                    Transaction.Builder builder = new Transaction.Builder(sourceAccount, tx.getNetwork())
//                            .setBaseFee(baseFee);
//                    for(Operation op : tx.getOperations()) {
//                        builder.addOperation(op);
//                    }
//
//                    if (tx.getTimeBounds() != null) {
//                        builder.addTimeBounds(tx.getTimeBounds());
//                    } else {
//                        builder.setTimeout(Transaction.Builder.TIMEOUT_INFINITE);
//                    }
//
//                    Transaction retryTx = builder.build();
//                    return server.submitTransaction(retryTx);
                }
				return server.submitTransaction(tx);
			} catch(Exception ex) {
				if(ex instanceof SubmitTransactionTimeoutResponseException) {
					rtn = (SubmitTransactionTimeoutResponseException) ex;
					logger.warn("Stellar TX submit timeout occured, trial = {}, tx = {}", i + 1, tx.toString());
                    logger.warn("TX timeout seq = {}, srcAccount = {}, memo = {}, fee = {}",
                            tx.getSequenceNumber(), tx.getSourceAccount(), tx.getMemo(), tx.getFee());
				} else {
                    logger.error("Failed TX finally timeout seq = {}, srcAccount = {}, memo = {}, fee = {}",
                            tx.getSequenceNumber(), tx.getSourceAccount(), tx.getMemo(), tx.getFee());
                    alarmService.error(logger, "Failed TX finally TX timeout seq = {}, srcAccount = {}, memo = {}, fee = {}",
                            tx.getSequenceNumber(), tx.getSourceAccount(), tx.getMemo(), tx.getFee());
					throw ex;
				}
			}
		}
		throw rtn;
	}
}
