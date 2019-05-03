package io.talken.dex.governance.service.bctx.processor;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.TokenMeta;
import io.talken.dex.governance.service.bctx.TxSender;
import io.talken.dex.governance.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.EthereumNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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

@Component
public class EthereumTxSender extends TxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumTxSender.class);

	@Autowired
	private SignServerService ssService;

	@Autowired
	private EthereumNetworkService ethereumNetworkService;

	public EthereumTxSender() {
		super(BlockChainPlatformEnum.ETHEREUM);
	}

	@Override
	public void sendTx(TokenMeta meta, Bctx bctx, BctxLog log) throws Exception {
		Web3j web3j = ethereumNetworkService.newClient();

		EthBlock.Block lastBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();

		EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(bctx.getAddressFrom(), DefaultBlockParameterName.LATEST).sendAsync().get();

		BigInteger nonce = ethGetTransactionCount.getTransactionCount();

		// TODO : get proper price
		BigInteger gasPrice = Convert.toWei("10", Convert.Unit.GWEI).toBigInteger();

		BigInteger gasLimit = lastBlock.getGasLimit();

		BigInteger amount = Convert.toWei(bctx.getAmount(), Convert.Unit.ETHER).toBigInteger();

		RawTransaction rawTx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, bctx.getAddressTo(), amount);

		log.setRequest(JSONWriter.toJsonString(rawTx));

		logger.debug("Request sign for {} bctx#{}", bctx.getAddressFrom(), bctx.getId());
		byte[] txSigned = ssService.signEthereumTransaction(rawTx, bctx.getAddressFrom());

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
