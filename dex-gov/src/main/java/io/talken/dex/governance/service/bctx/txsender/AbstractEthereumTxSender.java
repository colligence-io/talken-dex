package io.talken.dex.governance.service.bctx.txsender;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.abi.FunctionEncoder;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractEthereumTxSender extends TxSender {
	private final PrefixedLogger logger;

	@Autowired
	private EthereumNetworkService ethereumNetworkService;

	private static final BigInteger DEFAULT_ERC20_GASLIMIT = BigInteger.valueOf(100000L);

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
		String metaCA = null;
		if(meta.getAux() != null && meta.getAux().containsKey(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID)) {
			metaCA = meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString();
		}
		String bctxCA = bctx.getPlatformAux();

		if(metaCA == null && bctxCA == null) {
			return sendEthereumTx(null, meta.getUnitDecimals(), bctx, log);
		} else if(metaCA != null && bctxCA != null && metaCA.equals(bctxCA)) {
			return sendEthereumTx(meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString(), meta.getUnitDecimals(), bctx, log);
		} else {
			log.setStatus(BctxStatusEnum.FAILED);
			log.setErrorcode("CONTRACT_ID_NOT_MATCH");
			log.setErrormessage("CONTRACT_ID of bctx is not match with TokenMeta.");
			return false;
		}
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

		EthRpcClient infuraClient = ethereumNetworkService.getInfuraClient();

		Web3jService web3jService = infuraClient.newWeb3jService();
		Web3j web3j = Web3j.build(web3jService);

		final String from = bctx.getAddressFrom();

		BigInteger nonce = infuraClient.getNonce(web3jService, from);

		// to avoid parity-ethereum nextNonce race bug : https://github.com/paritytech/parity-ethereum/issues/10897
		if(nonceCheck.containsKey(from)) {
			// getNonce again (with 1 sec sleep) if new nonce is not greater than last successful nonce
			for(int nonceRetry = 0; nonceRetry < 5; nonceRetry++) { // retry 5 times interval 1 sec, or just go for it (will ends up with failed transaction anyway)
				if(nonce.compareTo(nonceCheck.get(from)) > 0) break; // nonce seems ok

				logger.warn("parity_nextNonce race condition detected : {} (retry = {})", from, nonceRetry);
				Thread.sleep(1000);
				nonce = infuraClient.getNonce(web3jService, from);
			}
		}

		BigInteger gasPrice = ethereumNetworkService.getGasPrice(web3j);

		BigInteger amount;
		if(decimals != null) {
			amount = bctx.getAmount().multiply(BigDecimal.TEN.pow(decimals)).toBigInteger();
		} else {
			amount = Convert.toWei(bctx.getAmount(), Convert.Unit.ETHER).toBigInteger();
		}

		BigInteger gasLimit = BigInteger.valueOf(21000); // 21000 for native ethereum gasLimit
		RawTransaction rawTx;

		if(contractAddr != null) {
			String encodedFunction = FunctionEncoder.encode(StandardERC20ContractFunctions.transfer(bctx.getAddressTo(), amount));

			// estimate gasLimit with given transaction
			Transaction est_tx = Transaction.createFunctionCallTransaction(
					from,
					nonce,
					gasPrice,
					BigInteger.ZERO,
					contractAddr,
					encodedFunction
			);

			try {
				BigInteger estAmountUsed = web3j.ethEstimateGas(est_tx).sendAsync().get().getAmountUsed();

				gasLimit = estAmountUsed.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN); // use 120% of estimated gaslimit
			} catch(Exception ex) {
				gasLimit = DEFAULT_ERC20_GASLIMIT;
				logger.warn("Cannot estimate ethereum tx gasLimit [{}], use default {}", ex.getClass().getSimpleName(), gasLimit);
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
			return true;
		} else {
			log.setErrorcode(Integer.toString(error.getCode()));
			log.setErrormessage(error.getMessage());
			return false;
		}
	}
}
