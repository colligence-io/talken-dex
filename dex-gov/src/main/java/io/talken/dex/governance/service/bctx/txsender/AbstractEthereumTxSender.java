package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.RunningProfile;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxSender;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.service.blockchain.ethereum.EthRpcClient;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import io.talken.dex.shared.service.blockchain.ethereum.StandardERC20ContractFunctions;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.abi.FunctionEncoder;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractEthereumTxSender extends TxSender {
	private final PrefixedLogger logger;

	@Autowired
	private EthereumNetworkService ethereumNetworkService;

    @Autowired
    private DSLContext dslContext;

    private static final BigInteger DEFAULT_GASLIMIT = BigInteger.valueOf(21000L);
	private static final BigInteger DEFAULT_ERC20_GASLIMIT = BigInteger.valueOf(78800L);
    private static final BigInteger DEFAULT_RFR_GASLIMIT = BigInteger.valueOf(200000L);
    private static final String RFR_CONTRACT = "0xd0929d411954c47438dc1d871dd6081f5c5e149c";

	private static Map<String, BigInteger> nonceCheck = new HashMap<>();

	public AbstractEthereumTxSender(BlockChainPlatformEnum platform, PrefixedLogger logger) {
		super(platform);
		this.logger = logger;
	}

	/**
	 * send ethereum tx
	 *
	 * @param meta
	 * @param bctx
	 * @param log
	 * @return
	 * @throws Exception
	 */
	@Override
	public boolean sendTx(TokenMetaTable.Meta meta, Bctx bctx, BctxLogRecord log) throws Exception {
		String metaCA = getMetaCA(meta, bctx);
		String bctxCA = getBctxCA(bctx);

		if(metaCA == null && bctxCA == null) {
			return sendEthereumTx(null, meta.getUnitDecimals(), bctx, log);
		} else if((metaCA != null && bctxCA != null) && metaCA.equals(bctxCA)) {
			return sendEthereumTx(meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString(), meta.getUnitDecimals(), bctx, log);
		} else {
			log.setStatus(BctxStatusEnum.FAILED);
			log.setErrorcode("CONTRACT_ID_NOT_MATCH");
			log.setErrormessage("CONTRACT_ID of bctx is not match with TokenMeta.");
            log.store();
			return false;
		}
	}

	public String getMetaCA(TokenMetaTable.Meta meta, Bctx bctx) {
        String metaCA = null;
        if(meta.getAux() != null && meta.getAux().containsKey(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID)) {
            metaCA = meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString();
        }
        return metaCA;
    }

    public String getBctxCA(Bctx bctx) {
        return bctx.getPlatformAux();
    }

	/**
	 * send ethereum tx (impl)
	 * NOTE use infura, NOT local node
	 *
	 * @param contractAddr
	 * @param decimals
	 * @param bctx
	 * @param log
	 * @return
	 * @throws Exception
	 */
	protected boolean sendEthereumTx(String contractAddr, Integer decimals, Bctx bctx, BctxLogRecord log) throws Exception {
		final String from = bctx.getAddressFrom();

        EthRpcClient ethRpcClient = ethereumNetworkService.getInfuraClient();
        Web3jService web3jService = ethRpcClient.newWeb3jService();
        Web3j web3j = Web3j.build(web3jService);

		BigInteger nonce = ethRpcClient.getNonce(web3jService, from);

		// to avoid parity-ethereum nextNonce race bug : https://github.com/paritytech/parity-ethereum/issues/10897
		if(nonceCheck.containsKey(from)) {
			// getNonce again (with 1 sec sleep) if new nonce is not greater than last successful nonce
			for(int nonceRetry = 0; nonceRetry < 5; nonceRetry++) { // retry 5 times interval 1 sec, or just go for it (will ends up with failed transaction anyway)
				if(nonce.compareTo(nonceCheck.get(from)) > 0) break; // nonce seems ok

				logger.warn("parity_nextNonce race condition detected : {} (retry = {})", from, nonceRetry);
				Thread.sleep(1000);
				nonce = ethRpcClient.getNonce(web3jService, from);
			}
		}

        BigInteger amount = getEthAmount(decimals, bctx);
		BigInteger gasPrice = ethereumNetworkService.getGasPrice(web3j);
		BigInteger gasLimit = DEFAULT_GASLIMIT;

		RawTransaction rawTx = generateRawTx(contractAddr, decimals, bctx, nonce, amount, gasPrice, gasLimit, from);

		log.setRequest(JSONWriter.toJsonString(rawTx));

		logger.info("[BCTX#{}] Request sign for {}", bctx.getId(), from);
		byte[] txSigned = signServer().signEthereumTransaction(rawTx, from);

		logger.info("[BCTX#{}] Sending TX to ethereum network. gas = {} gwei * {}", bctx.getId(), Convert.fromWei(gasPrice.toString(), Convert.Unit.GWEI), gasLimit);
		EthSendTransaction ethSendTx = web3j.ethSendRawTransaction(Numeric.toHexString(txSigned)).sendAsync().get();

		log.setResponse(JSONWriter.toJsonString(ethSendTx));

		Response.Error error = ethSendTx.getError();
		String txHash = ethSendTx.getTransactionHash();

		if(error == null) {
			// store last successful nonce
			nonceCheck.put(from, nonce);

			log.setBcRefId(txHash);
			log.setResponse(ethSendTx.getRawResponse());
			log.store();
			return true;
		} else {
			log.setErrorcode(Integer.toString(error.getCode()));
			log.setErrormessage(error.getMessage());
            log.store();
			return false;
		}
	}

    public boolean sendTxWithNonce(String contractAddr, Integer decimals, Bctx bctx, BctxLogRecord log, BigInteger nonce) throws Exception {
        EthRpcClient infuraClient = ethereumNetworkService.getInfuraClient();

        Web3jService web3jService = infuraClient.newWeb3jService();
        Web3j web3j = Web3j.build(web3jService);

        final String from = bctx.getAddressFrom();

        BigInteger amount = getEthAmount(decimals, bctx);
        BigInteger gasPrice = ethereumNetworkService.getGasPrice(web3j);
        BigInteger gasLimit = DEFAULT_GASLIMIT;

        RawTransaction rawTx = generateRawTx(contractAddr, decimals, bctx, nonce, amount, gasPrice, gasLimit, from);
        log.setRequest(JSONWriter.toJsonString(rawTx));

        logger.info("[BCTX#{}] Request sign for {}", bctx.getId(), from);
        byte[] txSigned = signServer().signEthereumTransaction(rawTx, from);

        logger.info("[BCTX#{}] Sending TX to ethereum network. gas = {} gwei * {}", bctx.getId(), Convert.fromWei(gasPrice.toString(), Convert.Unit.GWEI), gasLimit);
        EthSendTransaction ethSendTx = web3j.ethSendRawTransaction(Numeric.toHexString(txSigned)).sendAsync().get();

        log.setResponse(JSONWriter.toJsonString(ethSendTx));

        Response.Error error = ethSendTx.getError();
        String txHash = ethSendTx.getTransactionHash();

        if(error == null) {
            log.setBcRefId(txHash);
            log.setResponse(ethSendTx.getRawResponse());
            log.store();
            return true;
        } else {
            log.setErrorcode(Integer.toString(error.getCode()));
            log.setErrormessage(error.getMessage());
            log.store();
            return false;
        }
    }

    private RawTransaction generateRawTx(String contractAddr, Integer decimals, Bctx bctx,
                                         BigInteger nonce, BigInteger amount, BigInteger gasPrice, BigInteger gasLimit, String from ) {
        RawTransaction rawTx;

        if(contractAddr != null) {
            // estimate gasLimit with given transaction
//            EthRpcClient ethRpcClient = ethereumNetworkService.getInfuraClient();
//            Web3jService web3jService = ethRpcClient.newWeb3jService();
//            Web3j web3j = Web3j.build(web3jService);
//            Transaction est_tx = Transaction.createFunctionCallTransaction(
//                    from,
//                    nonce,
//                    gasPrice,
//                    BigInteger.ZERO,
//                    contractAddr,
//                    encodedFunction
//            );

//            try {
//                BigInteger estAmountUsed = web3j.ethEstimateGas(est_tx).sendAsync().get().getAmountUsed();
//                gasLimit = estAmountUsed.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN); // use 120% of estimated gaslimit
//            } catch(Exception ex) {
//                gasLimit = DEFAULT_ERC20_GASLIMIT;
//                logger.warn("Cannot estimate ethereum tx gasLimit [{}], use default {}", ex.getClass().getSimpleName(), gasLimit);
//            }

            String encodedFunction = FunctionEncoder.encode(StandardERC20ContractFunctions.transfer(bctx.getAddressTo(), amount));
            gasLimit = DEFAULT_ERC20_GASLIMIT;

            // for RFR
            if (RunningProfile.isProduction() && RFR_CONTRACT.equals(contractAddr)) {
                gasLimit = DEFAULT_RFR_GASLIMIT;
            }

            rawTx = RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    gasLimit, // use estimated gaslimit
                    contractAddr,
                    encodedFunction
            );

        } else {
            rawTx = RawTransaction.createEtherTransaction(
                    nonce,
                    gasPrice,
                    gasLimit,  // use 21000 fixed gaslimit for ethereum
                    bctx.getAddressTo(),
                    amount
            );
        }

        return rawTx;
    }
}
