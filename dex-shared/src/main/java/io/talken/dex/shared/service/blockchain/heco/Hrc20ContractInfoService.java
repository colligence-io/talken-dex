package io.talken.dex.shared.service.blockchain.heco;

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

/**
 * The type Hrc 20 contract info service.
 */
@Service
@Scope("singleton")
@RequiredArgsConstructor
public class Hrc20ContractInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(HecoNetworkService.class);

    /**
     * get hrc20 standard name, symbol, decimals
     * cached in redis
     *
     * @param web3j           the web 3 j
     * @param contractAddress the contract address
     * @return hrc 20 contract info
     * @throws Exception the exception
     */
    @Cacheable(value = CacheConfig.CacheNames.HECO_HRC20_CONTRACT_INFO, key = "#p1")
    public Hrc20ContractInfoService.Hrc20ContractInfo getHrc20ContractInfo(Web3j web3j, String contractAddress) throws Exception {
        ERC20 erc20 = ERC20.load(contractAddress, web3j, Credentials.create("0x0000000000000000000000000000000000000000000000000000000000000000"), new DefaultGasProvider());

        Hrc20ContractInfoService.Hrc20ContractInfo rtn = new Hrc20ContractInfoService.Hrc20ContractInfo();

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
        logger.debug("Caching contract HRC20 info : {} ({} / {} / {})", contractAddress, rtn.getName(), rtn.getSymbol(), rtn.getDecimals());
        return rtn;
    }

    /**
     * The type Hrc 20 contract info.
     */
    @Data
    public static class Hrc20ContractInfo {
        private String name = null;
        private String symbol = null;
        private BigInteger decimals = BigInteger.valueOf(18); // default 18 decimals
    }

    /**
     * get erc20 contract balance from network
     *
     * @param web3j           the web 3 j
     * @param contractAddress the contract address
     * @param owner           the owner
     * @return balance of
     * @throws Exception the exception
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
