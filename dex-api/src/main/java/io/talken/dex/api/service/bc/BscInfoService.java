package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.BscGasPriceResult;
import io.talken.dex.shared.exception.InternalServerErrorException;
import io.talken.dex.shared.service.blockchain.bsc.BscNetworkService;
import io.talken.dex.shared.service.blockchain.ethereum.Erc20ContractInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;

@Service
@Scope("singleton")
public class BscInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(BscInfoService.class);

    @Autowired
    private BscNetworkService bscNetworkService;

    private Web3j rpcClient;

    public BscInfoService() {
    }

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
        return new BigInteger("100000");
    }
}
