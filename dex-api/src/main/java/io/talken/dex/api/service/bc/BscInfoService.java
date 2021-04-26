package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.BscGasPriceResult;
import io.talken.dex.api.controller.dto.EthTransactionReceiptResultDTO;
import io.talken.dex.api.controller.dto.EthTransactionResultDTO;
import io.talken.dex.api.controller.dto.HecoGasPriceResult;
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

@Service
@Scope("singleton")
public class BscInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(BscInfoService.class);

    @Autowired
    private BscNetworkService bscNetworkService;

    @Autowired
    private Bep20ContractInfoService contractInfoService;

    private Web3j rpcClient;

    static final String DEFAULT_GASLIMIT = "21000";
    static final String DEFAULT_CONTRACT_GASLIMIT = "300000";
    static final String DEFAULT_CONTRACT_GASPRICE = "5000000000";

    @PostConstruct
    private void init() throws Exception {
        this.rpcClient = bscNetworkService.newMainRpcClient();
    }

    /**
     * get bsc balance
     *
     * @param address
     * @return
     * @throws GeneralException
     */
    public BigInteger getBscBalance(String address) throws GeneralException {
        return getBscBalance(rpcClient, address);
    }

    /**
     * get bsc balance
     *
     * @param web3j
     * @param address
     * @return
     * @throws GeneralException
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
     * @param contract
     * @param address
     * @return
     * @throws GeneralException
     */
    public BigInteger getBep20Balance(String contract, String address) throws GeneralException {
        return getBep20Balance(bscNetworkService.getClient().newClient(), contract, address);
    }

    /**
     * get bep20 balance
     *
     * @param web3j
     * @param contract
     * @param address
     * @return
     * @throws GeneralException
     */
    public BigInteger getBep20Balance(Web3j web3j, String contract, String address) throws GeneralException {
        try {
            return contractInfoService.getBalanceOf(web3j, contract, address);
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

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

    public BigInteger getGasPrice(Web3j web3j) throws IOException {
        // recommended from lambda256
        return web3j.ethGasPrice().send().getGasPrice();
    }

    public BigInteger getGasLimit(Web3j web3j) {
        return new BigInteger(DEFAULT_GASLIMIT);
    }

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
    public BigInteger getBep20GasPrice(Web3j web3j) throws IOException {
        // expected value. 5 Gwei is default.
        return new BigInteger(DEFAULT_CONTRACT_GASPRICE);
    }

    public BigInteger getBep20GasLimit(Web3j web3j) throws IOException {
        return new BigInteger(DEFAULT_CONTRACT_GASLIMIT);
    }

    public EthTransactionResultDTO getBscTransaction(String txHash) throws ExecutionException, InterruptedException {
        EthTransactionResultDTO.EthTransactionResultDTOBuilder builder = EthTransactionResultDTO.builder();
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

    public EthTransactionReceiptResultDTO getBscTransactionReceipt(String txHash) throws ExecutionException, InterruptedException {
        EthTransactionReceiptResultDTO.EthTransactionReceiptResultDTOBuilder builder = EthTransactionReceiptResultDTO.builder();
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
