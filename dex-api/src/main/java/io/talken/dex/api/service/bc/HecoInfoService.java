package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.BscGasPriceResult;
import io.talken.dex.api.controller.dto.EthTransactionReceiptResultDTO;
import io.talken.dex.api.controller.dto.EthTransactionResultDTO;
import io.talken.dex.api.controller.dto.HecoGasPriceResult;
import io.talken.dex.shared.exception.InternalServerErrorException;
import io.talken.dex.shared.service.blockchain.bsc.Bep20ContractInfoService;
import io.talken.dex.shared.service.blockchain.heco.HecoNetworkService;
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
public class HecoInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(HecoInfoService.class);

    @Autowired
    private HecoNetworkService hecoNetworkService;

    @Autowired
    private Bep20ContractInfoService contractInfoService;

    private Web3j rpcClient;

    @PostConstruct
    private void init() throws Exception {
        this.rpcClient = hecoNetworkService.newMainRpcClient();
    }

    /**
     * get heco balance
     *
     * @param address
     * @return
     * @throws GeneralException
     */
    public BigInteger getHecoBalance(String address) throws GeneralException {
        return getHecoBalance(rpcClient, address);
    }

    /**
     * get heco balance
     *
     * @param web3j
     * @param address
     * @return
     * @throws GeneralException
     */
    public BigInteger getHecoBalance(Web3j web3j, String address) throws GeneralException {
        try {
            return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * get heco gas price and limit
     *
     * @return
     * @throws InternalServerErrorException
     */
    public HecoGasPriceResult getGasPriceAndLimit() throws InternalServerErrorException {
        try {
            HecoGasPriceResult rtn = new HecoGasPriceResult();
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
        return new BigInteger("21000");
    }

    /**
     * get heco transaction by hash
     * @param txHash
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public EthTransactionResultDTO getHecoTransaction(String txHash) throws ExecutionException, InterruptedException {
        EthTransactionResultDTO.EthTransactionResultDTOBuilder builder = EthTransactionResultDTO.builder();
        if (txHash != null) {
            Transaction tx = hecoNetworkService.getHecoTransaction(txHash);
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
     * get heco transaction receipt by hash
     * @param txHash
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public EthTransactionReceiptResultDTO getHecoTransactionReceipt(String txHash) throws ExecutionException, InterruptedException {
        EthTransactionReceiptResultDTO.EthTransactionReceiptResultDTOBuilder builder = EthTransactionReceiptResultDTO.builder();
        if (txHash != null) {
            TransactionReceipt tx = hecoNetworkService.getHecoTransactionReceipt(txHash);
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
