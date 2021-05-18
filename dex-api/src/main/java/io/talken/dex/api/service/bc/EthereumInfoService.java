package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.EthTransactionReceiptResult;
import io.talken.dex.api.controller.dto.EthTransactionResult;
import io.talken.dex.api.controller.dto.PendingTxListRequest;
import io.talken.dex.api.controller.dto.PendingTxListResult;
import io.talken.dex.shared.service.blockchain.ethereum.Erc20ContractInfoService;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static io.talken.common.persistence.jooq.Tables.BCTX;

/**
 * The type Ethereum info service.
 */
@Service
@Scope("singleton")
public class EthereumInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumInfoService.class);

	@Autowired
	private EthereumNetworkService ethNetworkService;

	@Autowired
	private Erc20ContractInfoService contractInfoService;

    @Autowired
    private DSLContext dslContext;

    /**
     * get eth balance
     *
     * @param address the address
     * @return eth balance
     * @throws GeneralException the general exception
     */
    public BigInteger getEthBalance(String address) throws GeneralException {
		return getEthBalance(ethNetworkService.getLocalClient().newClient(), address);
	}

    /**
     * get eth balance
     *
     * @param web3j   the web 3 j
     * @param address the address
     * @return eth balance
     * @throws GeneralException the general exception
     */
    public BigInteger getEthBalance(Web3j web3j, String address) throws GeneralException {
		try {
			return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
		} catch(Exception ex) {
			throw new GeneralException(ex);
		}
	}

    /**
     * get erc20 balance
     *
     * @param contract the contract
     * @param address  the address
     * @return erc 20 balance
     * @throws GeneralException the general exception
     */
    public BigInteger getErc20Balance(String contract, String address) throws GeneralException {
		return getErc20Balance(ethNetworkService.getLocalClient().newClient(), contract, address);
	}

    /**
     * get erc20 balance
     *
     * @param web3j    the web 3 j
     * @param contract the contract
     * @param address  the address
     * @return erc 20 balance
     * @throws GeneralException the general exception
     */
    public BigInteger getErc20Balance(Web3j web3j, String contract, String address) throws GeneralException {
		try {
			return contractInfoService.getBalanceOf(web3j, contract, address);
		} catch(Exception ex) {
			throw new GeneralException(ex);
		}
	}

    /**
     * Gets pending transaction tx list.
     *
     * @param request the request
     * @return the pending transaction tx list
     */
    public PendingTxListResult getPendingTransactionTxList(PendingTxListRequest request) {
	    // TODO : add condition
        PendingTxListResult result = new PendingTxListResult();
        Condition cond = BCTX.STATUS.eq(BctxStatusEnum.SENT)
                .and(BCTX.BC_REF_ID.isNotNull())
                .and(BCTX.SYMBOL.eq(request.getSymbol()))
                .and(BCTX.BCTX_TYPE.eq(BlockChainPlatformEnum.ETHEREUM).or(BCTX.BCTX_TYPE.eq(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN)))
                .and(BCTX.ADDRESS_FROM.eq(request.getAddress()).or(BCTX.ADDRESS_TO.eq(request.getAddress())));
//                                            .and(BCTX.UPDATE_TIMESTAMP.isNotNull()
//                                                    .and(BCTX.UPDATE_TIMESTAMP.le(UTCUtil.getNow().minusSeconds(30)))
//                                                    .and(BCTX.UPDATE_TIMESTAMP.gt(UTCUtil.getNow().minusDays(7))))

        List<Bctx> records = dslContext.selectFrom(BCTX)
                .where(cond)
                .fetch().into(BCTX).into(Bctx.class);

        result.setRecordCount(records.size());
        result.setBctxs(records);

	    return result;
    }

    /**
     * for test
     *
     * @param address the address
     * @return transaction count
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public Map<String, BigInteger> getTransactionCount(String address) throws ExecutionException, InterruptedException {
        Map<String, BigInteger> map = new HashMap<>();
        Web3j web3j = ethNetworkService.getLocalClient().newClient();
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
     * Gets eth transaction.
     *
     * @param txHash the tx hash
     * @return the eth transaction
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public EthTransactionResult getEthTransaction(String txHash) throws ExecutionException, InterruptedException {
        EthTransactionResult.EthTransactionResultBuilder builder = EthTransactionResult.builder();
        if (txHash != null) {
            Transaction tx = ethNetworkService.getEthTransaction(txHash);
            if (tx != null) {
                builder.blockHash(tx.getBlockHash())
                        .blockNumber(tx.getBlockNumberRaw())
                        .from(tx.getFrom())
                        .gas(tx.getGasRaw())
                        .gasPrice(tx.getGasPriceRaw())
                        .hash(tx.getHash())
                        .input(tx.getInput())
                        .nonce(tx.getNonceRaw())
                        .to(tx.getTo())
                        .transactionIndex(tx.getTransactionIndexRaw())
                        .value(tx.getValueRaw())
                        .v(tx.getV())
                        .s(tx.getS())
                        .r(tx.getR());

                if (tx.getCreates() != null) builder.creates(tx.getCreates());
                if (tx.getChainId() != null) builder.chainId(tx.getChainId());
                if (tx.getPublicKey() != null) builder.publicKey(tx.getPublicKey());
            }
        }

        return builder.build();
    }

    /**
     * Gets eth transaction receipt.
     *
     * @param txHash the tx hash
     * @return the eth transaction receipt
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public EthTransactionReceiptResult getEthTransactionReceipt(String txHash) throws ExecutionException, InterruptedException {
        EthTransactionReceiptResult.EthTransactionReceiptResultBuilder builder = EthTransactionReceiptResult.builder();
        if (txHash != null) {
            TransactionReceipt tx = ethNetworkService.getEthTransactionReceipt(txHash);
            if (tx != null) {
                builder.transactionHash(tx.getTransactionHash())
                        .transactionIndex(tx.getTransactionIndex())
                        .blockHash(tx.getBlockHash())
                        .blockNumber(tx.getBlockNumberRaw())
                        .from(tx.getFrom())
                        .to(tx.getTo())
                        .cumulativeGasUsed(tx.getCumulativeGasUsed())
                        .gasUsed(tx.getGasUsed())
                        .contractAddress(tx.getContractAddress())
                        .logs(tx.getLogs())
                        .logsBloom(tx.getLogsBloom())
                        .root(tx.getRoot())
                        .status(tx.getStatus());

                builder.revertReason(tx.getRevertReason());
                builder.isStatus(tx.isStatusOK());
            }
        }

        return builder.build();
    }
}
