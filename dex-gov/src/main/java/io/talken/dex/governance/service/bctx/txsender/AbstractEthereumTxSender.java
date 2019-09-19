package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.TokenMeta;
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
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;

public abstract class AbstractEthereumTxSender extends TxSender {
	private final PrefixedLogger logger;

	@Autowired
	private EthereumNetworkService ethereumNetworkService;

	public AbstractEthereumTxSender(BlockChainPlatformEnum platform, PrefixedLogger logger) {
		super(platform);
		this.logger = logger;
	}

	@Override
	public boolean sendTx(TokenMeta meta, Bctx bctx, BctxLogRecord log) throws Exception {
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

	protected boolean sendEthereumTx(String contractAddr, Integer decimals, Bctx bctx, BctxLogRecord log) throws Exception {
		Web3j web3j = ethereumNetworkService.newClient();

		BigInteger nonce = web3j.ethGetTransactionCount(bctx.getAddressFrom(), DefaultBlockParameterName.LATEST).send().getTransactionCount();
		BigInteger nonce_p = web3j.ethGetTransactionCount(bctx.getAddressFrom(), DefaultBlockParameterName.PENDING).send().getTransactionCount();
		if(nonce_p != null && (nonce_p.compareTo(nonce) > 0)) {
			nonce = nonce_p;
		}

		BigInteger gasPrice = ethereumNetworkService.getGasPrice(web3j);

		BigInteger gasLimit = ethereumNetworkService.getGasLimit(web3j);

		BigInteger amount;
		if(decimals != null) {
			amount = bctx.getAmount().multiply(BigDecimal.TEN.pow(decimals)).toBigInteger();
		} else {
			amount = Convert.toWei(bctx.getAmount(), Convert.Unit.ETHER).toBigInteger();
		}

		RawTransaction rawTx;

		if(contractAddr != null) {
			Function function = StandardERC20ContractFunctions.transfer(bctx.getAddressTo(), amount);

			String encodedFunction = FunctionEncoder.encode(function);

			rawTx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddr, encodedFunction);
		} else {
			rawTx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, bctx.getAddressTo(), amount);
		}


		if(contractAddr != null) {
			rawTx = RawTransaction.createTransaction(
					nonce,
					gasPrice,
					gasLimit,
					contractAddr,
					FunctionEncoder.encode(StandardERC20ContractFunctions.transfer(bctx.getAddressTo(), amount))
			);

		} else {
			rawTx = RawTransaction.createEtherTransaction(
					nonce,
					gasPrice,
					gasLimit,
					bctx.getAddressTo(),
					amount
			);
		}

		log.setRequest(JSONWriter.toJsonString(rawTx));

		logger.info("[BCTX#{}] Request sign for {}", bctx.getId(), bctx.getAddressFrom());
		byte[] txSigned = signServer().signEthereumTransaction(rawTx, bctx.getAddressFrom());

		logger.info("[BCTX#{}] Sending TX to ethereum network.", bctx.getId());
		EthSendTransaction ethSendTx = web3j.ethSendRawTransaction(Numeric.toHexString(txSigned)).send();

		log.setResponse(JSONWriter.toJsonString(ethSendTx));

		Response.Error error = ethSendTx.getError();
		String txHash = ethSendTx.getTransactionHash();

		if(error == null) {
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
