package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.BscGasPriceResult;
import io.talken.dex.api.controller.dto.EthTransactionReceiptResult;
import io.talken.dex.api.controller.dto.EthTransactionResult;
import io.talken.dex.shared.exception.InternalServerErrorException;
import io.talken.dex.shared.service.blockchain.bsc.Bep20ContractInfoService;
import io.talken.dex.shared.service.blockchain.bsc.BscNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

/**
 * The type Bsc info service.
 */
@Service
@Scope("singleton")
public class BscInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(BscInfoService.class);

    @Autowired
    private BscNetworkService bscNetworkService;

    @Autowired
    private Bep20ContractInfoService contractInfoService;

    private Web3j rpcClient;

    /**
     * The Default gaslimit.
     */
    static final String DEFAULT_GASLIMIT = "21000";
    /**
     * The Default contract gaslimit.
     */
    static final String DEFAULT_CONTRACT_GASLIMIT = "300000";
    /**
     * The Default contract gasprice.
     */
    static final String DEFAULT_CONTRACT_GASPRICE = "5000000000";

    @PostConstruct
    private void init() throws Exception {
        this.rpcClient = bscNetworkService.newMainRpcClient();
    }

    /**
     * get bsc balance
     *
     * @param address the address
     * @return bsc balance
     * @throws GeneralException the general exception
     */
    public BigInteger getBscBalance(String address) throws GeneralException {
        return getBscBalance(rpcClient, address);
    }

    /**
     * get bsc balance
     *
     * @param web3j   the web 3 j
     * @param address the address
     * @return bsc balance
     * @throws GeneralException the general exception
     */
    public BigInteger getBscBalance(Web3j web3j, String address) throws GeneralException {
        try {
            return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * get bep20 balance
     *
     * @param contract the contract
     * @param address  the address
     * @return bep 20 balance
     * @throws GeneralException the general exception
     */
    public BigInteger getBep20Balance(String contract, String address) throws GeneralException {
        return getBep20Balance(bscNetworkService.getClient().newClient(), contract, address);
    }

    /**
     * get bep20 balance
     *
     * @param web3j    the web 3 j
     * @param contract the contract
     * @param address  the address
     * @return bep 20 balance
     * @throws GeneralException the general exception
     */
    public BigInteger getBep20Balance(Web3j web3j, String contract, String address) throws GeneralException {
        try {
            return contractInfoService.getBalanceOf(web3j, contract, address);
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * Gets gas price and limit.
     *
     * @return the gas price and limit
     * @throws InternalServerErrorException the internal server error exception
     */
    public BscGasPriceResult getGasPriceAndLimit() throws InternalServerErrorException {
        try {
            BscGasPriceResult rtn = new BscGasPriceResult();
            rtn.setGasPrice(getGasPrice(this.rpcClient));
            rtn.setGasLimit(getGasLimit(this.rpcClient));
            return rtn;
        } catch(Exception e) {
            throw new InternalServerErrorException(e);
        }
    }

    /**
     * Gets gas price.
     *
     * @param web3j the web 3 j
     * @return the gas price
     * @throws IOException the io exception
     */
    public BigInteger getGasPrice(Web3j web3j) throws IOException {
        // recommended from lambda256
        return web3j.ethGasPrice().send().getGasPrice();
    }

    /**
     * Gets gas limit.
     *
     * @param web3j the web 3 j
     * @return the gas limit
     */
    public BigInteger getGasLimit(Web3j web3j) {
        return new BigInteger(DEFAULT_GASLIMIT);
    }

    /**
     * Gets bep 20 gas price and limit.
     *
     * @return the bep 20 gas price and limit
     * @throws InternalServerErrorException the internal server error exception
     */
// TODO: need to calculate gas price for contract tokens
    public BscGasPriceResult getBep20GasPriceAndLimit() throws InternalServerErrorException {
        try {
            BscGasPriceResult rtn = new BscGasPriceResult();
            rtn.setGasPrice(getBep20GasPrice(this.rpcClient));
            rtn.setGasLimit(getBep20GasLimit(this.rpcClient));
            return rtn;
        } catch(Exception e) {
            throw new InternalServerErrorException(e);
        }
    }

    /**
     * Gets bep 20 gas price.
     *
     * @param web3j the web 3 j
     * @return the bep 20 gas price
     * @throws IOException the io exception
     */
    public BigInteger getBep20GasPrice(Web3j web3j) throws IOException {
        // expected value. 5 Gwei is default.
        return new BigInteger(DEFAULT_CONTRACT_GASPRICE);
    }

    /**
     * Gets bep 20 gas limit.
     *
     * @param web3j the web 3 j
     * @return the bep 20 gas limit
     * @throws IOException the io exception
     */
    public BigInteger getBep20GasLimit(Web3j web3j) throws IOException {
        return new BigInteger(DEFAULT_CONTRACT_GASLIMIT);
    }

    /**
     * Gets bsc transaction.
     *
     * @param txHash the tx hash
     * @return the bsc transaction
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public EthTransactionResult getBscTransaction(String txHash) throws ExecutionException, InterruptedException {
        EthTransactionResult.EthTransactionResultBuilder builder = EthTransactionResult.builder();
        if (txHash != null) {
            Transaction tx = bscNetworkService.getBscTransaction(txHash);
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
     * Gets bsc transaction receipt.
     *
     * @param txHash the tx hash
     * @return the bsc transaction receipt
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public EthTransactionReceiptResult getBscTransactionReceipt(String txHash) throws ExecutionException, InterruptedException {
        EthTransactionReceiptResult.EthTransactionReceiptResultBuilder builder = EthTransactionReceiptResult.builder();
        if (txHash != null) {
            TransactionReceipt tx = bscNetworkService.getBscTransactionReceipt(txHash);
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
