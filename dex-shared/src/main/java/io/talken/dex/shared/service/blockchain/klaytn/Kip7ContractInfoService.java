package io.talken.dex.shared.service.blockchain.klaytn;

import com.klaytn.caver.abi.Function;
import com.klaytn.caver.abi.FunctionEncoder;
import com.klaytn.caver.abi.FunctionReturnDecoder;
import com.klaytn.caver.abi.datatypes.Type;
import com.klaytn.caver.abi.datatypes.generated.Uint256;
import com.klaytn.caver.kct.kip7.KIP7ConstantData;
import com.klaytn.caver.methods.request.CallObject;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.config.CacheConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import org.web3j.protocol.core.Response;
import xyz.groundx.caver_ext_kas.CaverExtKAS;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.model.FtContractDetail;

import java.math.BigInteger;
import java.util.List;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class Kip7ContractInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(Kip7ContractInfoService.class);

    /**
     * get bep20 standard name, symbol, decimals
     * cached in redis
     *
     * @param caver
     * @param contractAddress
     * @return
     * @throws Exception
     */
//    @Cacheable(value = CacheConfig.CacheNames.BSC_BEP20_CONTRACT_INFO, key = "#p1")
    public Kip7ContractInfoService.Kip7ContractInfo getKip7ContractInfo(CaverExtKAS caver, String contractAddress) throws Exception {
        FtContractDetail ftContract = caver.kas.tokenHistory.getFTContract(contractAddress);

        Kip7ContractInfoService.Kip7ContractInfo rtn = new Kip7ContractInfoService.Kip7ContractInfo();

        try {
//            rtn.setSymbol(kip7.symbol());
            rtn.setSymbol(ftContract.getSymbol());
        } catch(Exception ex) {
//			logger.exception(ex);
        }

        try {
//            rtn.setName(kip7.name());
            rtn.setName(ftContract.getName());
        } catch(Exception ex) {
//			logger.exception(ex);
        }

        try {
//            rtn.setDecimals(BigInteger.valueOf(kip7.decimals()));
            rtn.setDecimals(BigInteger.valueOf(ftContract.getDecimals()));
        } catch(Exception ex) {
//			logger.exception(ex);
        }
        logger.debug("Caching contract KIP7 info : {} ({} / {} / {})", contractAddress, rtn.getName(), rtn.getSymbol(), rtn.getDecimals());
        return rtn;
    }

    @Data
    public static class Kip7ContractInfo {
        private String name = null;
        private String symbol = null;
        private BigInteger decimals = BigInteger.valueOf(18); // default 18 decimals
    }

    /**
     * get erc20 contract balance from network
     *
     * @param caver
     * @param contractAddress
     * @param owner
     * @return
     * @throws Exception
     */
    public BigInteger getBalanceOf(CaverExtKAS caver, String contractAddress, String owner) throws Exception {

        String abi = KIP7ConstantData.ABI;

        Function function = StandardKIP7ContractFunctions.balanceOf(owner);

        // #1
//        new Request<>(
//                "klay_call",
//                Collections.<String>emptyList(),
//                caver.rpc.getWeb3jService(),
//                Bytes.class).send();

        // #2
        CallObject obj = CallObject.createCallObject();
//        obj.setFrom(owner);
        obj.setTo(contractAddress);
        obj.setData(FunctionEncoder.encode(function));

        Response<String> response = caver.rpc.klay.call(obj).send();

        List<Type> decoded = FunctionReturnDecoder.decode(response.getResult(), function.getOutputParameters());

        if(decoded.size() > 0)
            return ((Uint256) decoded.get(0)).getValue();
        else
            return null;
    }
}
