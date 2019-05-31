package io.talken.dex.governance.service.bctx.processor;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxSender;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import io.talken.dex.shared.service.blockchain.ethereum.StandardERC20ContractFunctions;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

public abstract class AbstractEthereumTxSender extends TxSender {
	private final PrefixedLogger logger;

	@Autowired
	private EthereumNetworkService ethereumNetworkService;

	public AbstractEthereumTxSender(BlockChainPlatformEnum platform, PrefixedLogger logger) {
		super(platform);
		this.logger = logger;
	}

	protected void sendEthereumTx(String contractAddr, Bctx bctx, BctxLog log) throws Exception {
		Web3j web3j = ethereumNetworkService.newClient();

		EthBlock.Block lastBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();

		EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(bctx.getAddressFrom(), DefaultBlockParameterName.LATEST).sendAsync().get();

		BigInteger nonce = ethGetTransactionCount.getTransactionCount();

		BigInteger gasPrice = Convert.toWei(String.valueOf(ethereumNetworkService.getNetworkFee()), Convert.Unit.GWEI).toBigInteger();

		BigInteger gasLimit = lastBlock.getGasLimit();

		BigInteger amount = Convert.toWei(bctx.getAmount(), Convert.Unit.ETHER).toBigInteger();

		RawTransaction rawTx;

		if(contractAddr != null) {
			Function function = StandardERC20ContractFunctions.transfer(bctx.getAddressTo(), amount);

			String encodedFunction = FunctionEncoder.encode(function);

			rawTx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddr, encodedFunction);
		} else {
			rawTx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, bctx.getAddressTo(), amount);
		}

		log.setRequest(JSONWriter.toJsonString(rawTx));

		logger.debug("Request sign for {} bctx#{}", bctx.getAddressFrom(), bctx.getId());
		byte[] txSigned = signServer().signEthereumTransaction(rawTx, bctx.getAddressFrom());

		logger.debug("Sending TX to ethereum network.");
		EthSendTransaction ethSendTx = web3j.ethSendRawTransaction(Numeric.toHexString(txSigned)).sendAsync().get();

		log.setResponse(JSONWriter.toJsonString(ethSendTx));

		Response.Error error = ethSendTx.getError();
		String txHash = ethSendTx.getTransactionHash();

		if(error == null) {
			log.setSuccessFlag(true);
			log.setBcRefId(txHash);
		} else {
			log.setSuccessFlag(false);
			log.setErrorcode(Integer.toString(error.getCode()));
			log.setErrormessage(error.getMessage());
		}
	}
}
