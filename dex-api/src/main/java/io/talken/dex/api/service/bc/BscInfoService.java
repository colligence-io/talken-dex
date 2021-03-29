package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.service.blockchain.bsc.BscNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;

@Service
@Scope("singleton")
public class BscInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(BscInfoService.class);

    @Autowired
    private BscNetworkService bscNetworkService;

    /**
     * get eth balance
     *
     * @param address
     * @return
     * @throws GeneralException
     */
    public BigInteger getBalance(String address) throws GeneralException {
        return getBalance(address);
    }
}
