package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.service.blockchain.bsc.Bep20ContractInfoService;
import io.talken.dex.shared.service.blockchain.heco.HecoNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import javax.annotation.PostConstruct;
import java.math.BigInteger;

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
}
