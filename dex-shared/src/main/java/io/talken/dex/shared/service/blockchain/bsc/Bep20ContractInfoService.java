package io.talken.dex.shared.service.blockchain.bsc;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.config.CacheConfig;
import io.talken.dex.shared.service.blockchain.ethereum.StandardERC20ContractFunctions;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.contracts.eip20.generated.ERC20;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.util.List;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class Bep20ContractInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(BscNetworkService.class);

    /**
     * get bep20 standard name, symbol, decimals
     * cached in redis
     *
     * @param web3j
     * @param contractAddress
     * @return
     * @throws Exception
     */
    @Cacheable(value = CacheConfig.CacheNames.BSC_BEP20_CONTRACT_INFO, key = "#p1")
    public Bep20ContractInfoService.Bep20ContractInfo getBep20ContractInfo(Web3j web3j, String contractAddress) throws Exception {
        ERC20 erc20 = ERC20.load(contractAddress, web3j, Credentials.create("0x0000000000000000000000000000000000000000000000000000000000000000"), new DefaultGasProvider());

        Bep20ContractInfoService.Bep20ContractInfo rtn = new Bep20ContractInfoService.Bep20ContractInfo();

        try {
            rtn.setSymbol(erc20.symbol().send());
        } catch(Exception ex) {
//			logger.exception(ex);
        }

        try {
            rtn.setName(erc20.name().send());
        } catch(Exception ex) {
//			logger.exception(ex);
        }

        try {
            rtn.setDecimals(erc20.decimals().send());
        } catch(Exception ex) {
//			logger.exception(ex);
        }
        logger.debug("Caching contract BEP20 info : {} ({} / {} / {})", contractAddress, rtn.getName(), rtn.getSymbol(), rtn.getDecimals());
        return rtn;
    }

    @Data
    public static class Bep20ContractInfo {
        private String name = null;
        private String symbol = null;
        private BigInteger decimals = BigInteger.valueOf(18); // default 18 decimals
    }

    /**
     * get erc20 contract balance from network
     *
     * @param web3j
     * @param contractAddress
     * @param owner
     * @return
     * @throws Exception
     */
    public BigInteger getBalanceOf(Web3j web3j, String contractAddress, String owner) throws Exception {
        Function function = StandardERC20ContractFunctions.balanceOf(owner);

        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        owner,
                        contractAddress,
                        FunctionEncoder.encode(function)
                ),
                DefaultBlockParameterName.LATEST
        ).send();

        List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

        if(decoded.size() > 0)
            return ((Uint256) decoded.get(0)).getValue();
        else
            return null;
    }
}
