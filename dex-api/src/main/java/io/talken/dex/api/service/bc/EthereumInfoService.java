package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.service.blockchain.ethereum.Erc20ContractInfoService;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@Scope("singleton")
public class EthereumInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumInfoService.class);

	@Autowired
	private EthereumNetworkService ethereumNetworkService;

	@Autowired
	private Erc20ContractInfoService contractInfoService;

	/**
	 * get eth balance
	 *
	 * @param address
	 * @return
	 * @throws GeneralException
	 */
	public BigInteger getEthBalance(String address) throws GeneralException {
		return getEthBalance(ethereumNetworkService.getLocalClient().newClient(), address);
	}

	/**
	 * get eth balance
	 *
	 * @param web3j
	 * @param address
	 * @return
	 * @throws GeneralException
	 */
	public BigInteger getEthBalance(Web3j web3j, String address) throws GeneralException {
		try {
			return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
		} catch(Exception ex) {
			throw new GeneralException(ex);
		}
	}

	public Map<String, BigInteger> getTransactionCount(String address) throws ExecutionException, InterruptedException {
        Map<String, BigInteger> map = new HashMap<>();
        Web3j web3j = ethereumNetworkService.getLocalClient().newClient();
//        web3j.web3ClientVersion().flowable().subscribe(
//                x -> {
//                    logger.info("subscribe::{}", x.getWeb3ClientVersion());
//                }
//        );
        EthGetTransactionCount ethGetTransactionCountP = web3j
                .ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
                .sendAsync().get();
        EthGetTransactionCount ethGetTransactionCountL = web3j
                .ethGetTransactionCount(address, DefaultBlockParameterName.LATEST)
                .sendAsync().get();
        EthGetTransactionCount ethGetTransactionCountE = web3j
                .ethGetTransactionCount(address, DefaultBlockParameterName.EARLIEST)
                .sendAsync().get();
        logger.debug("EthGetTransactionCount P :: {}", ethGetTransactionCountP.getTransactionCount());
        logger.debug("EthGetTransactionCount L :: {}", ethGetTransactionCountL.getTransactionCount());
        logger.debug("EthGetTransactionCount E :: {}", ethGetTransactionCountE.getTransactionCount());

        EthBlockNumber ethBlockNumber = web3j.ethBlockNumber().sendAsync().get();
        logger.debug("EthBlockNumber :: {}", ethBlockNumber.getBlockNumber());

        EthBlock ethBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, true).sendAsync().get();
        logger.debug("EthBlock :: number {}", ethBlock.getBlock().getNumber());
        logger.debug("EthBlock :: Hash {}", ethBlock.getBlock().getHash());
        logger.debug("EthBlock :: Tx.length {}", ethBlock.getBlock().getTransactions().size());

        BigInteger currentBlock = ethBlock.getBlock().getNumber();

        EthGetTransactionCount ethGetTransactionCountC = web3j
                .ethGetTransactionCount(address, DefaultBlockParameter.valueOf(ethBlock.getBlock().getNumber()))
                .sendAsync().get();

        logger.debug("EthGetTransactionCount C :: {}", ethGetTransactionCountC.getTransactionCount());

        map.put("EthGetTransactionCount_P", ethGetTransactionCountP.getTransactionCount());
        map.put("EthGetTransactionCount_L", ethGetTransactionCountL.getTransactionCount());
        map.put("EthGetTransactionCount_E", ethGetTransactionCountE.getTransactionCount());
        map.put("EthGetBlock", currentBlock);
        map.put("EthGetTransactionCount_C", ethGetTransactionCountC.getTransactionCount());

        return map;
    }

	/**
	 * get erc20 balance
	 *
	 * @param contract
	 * @param address
	 * @return
	 * @throws GeneralException
	 */
	public BigInteger getErc20Balance(String contract, String address) throws GeneralException {
		return getErc20Balance(ethereumNetworkService.getLocalClient().newClient(), contract, address);
	}

	/**
	 * get erc20 balance
	 *
	 * @param web3j
	 * @param contract
	 * @param address
	 * @return
	 * @throws GeneralException
	 */
	public BigInteger getErc20Balance(Web3j web3j, String contract, String address) throws GeneralException {
		try {
			return contractInfoService.getBalanceOf(web3j, contract, address);
		} catch(Exception ex) {
			throw new GeneralException(ex);
		}
	}
}
