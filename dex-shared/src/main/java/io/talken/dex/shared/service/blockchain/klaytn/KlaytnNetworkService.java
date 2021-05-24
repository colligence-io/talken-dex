package io.talken.dex.shared.service.blockchain.klaytn;

import com.klaytn.caver.contract.SendOptions;
import com.klaytn.caver.kct.kip7.KIP7;
import com.klaytn.caver.methods.response.Quantity;
import com.klaytn.caver.methods.response.TransactionReceipt;
import com.klaytn.caver.utils.Utils;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.utils.Numeric;
import xyz.groundx.caver_ext_kas.CaverExtKAS;
import xyz.groundx.caver_ext_kas.kas.wallet.Wallet;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.ApiException;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.wallet.model.TransactionResult;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.wallet.model.ValueTransferTransactionRequest;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The type Klaytn network service.
 */
@Service
@Scope("singleton")
@RequiredArgsConstructor
public class KlaytnNetworkService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(KlaytnNetworkService.class);

    private final DexSettings dexSettings;

    private KlayKasRpcClient klayKasRpcClient;

    @PostConstruct
    private void init() throws IOException {
        final int chainId = dexSettings.getBcnode().getKlaytn().getChainId();
        final String accessKeyId = dexSettings.getBcnode().getKlaytn().getAccessKeyId();
        final String secretAccessKey = dexSettings.getBcnode().getKlaytn().getSecretAccessKey();

        this.klayKasRpcClient = new KlayKasRpcClient(chainId, accessKeyId, secretAccessKey);

        Quantity response = this.klayKasRpcClient.getClient().rpc.klay.getBlockNumber().send();
        logger.info("Using Klaytn {} Network : {} {}", chainId, response.getValue(), "");
    }

    /**
     * Gets kas client.
     *
     * @return the kas client
     */
    public KlayKasRpcClient getKasClient() {
        return this.klayKasRpcClient;
    }

    public TransactionResult send(String symbol, String to, BigDecimal amount) throws ApiException {
        CaverExtKAS caver = this.getKasClient().getClient();

        Wallet wallet = caver.wallet.getWalletAPI();
        String _amount = Utils.convertToPeb(amount, Utils.KlayUnit.KLAY);
        BigInteger intAmount = new BigDecimal(_amount).toBigInteger();
        String hexValue = Numeric.toHexStringWithPrefix(intAmount);

        // TODO: need to vault or hardcoded
        // 0xDC314F64067FA7729BBFaC4a2Db1e6148De3041C
        // 0x04aa6649fe9a3265b4a85e15236495d3837fb5aad4fcc716a6e82625cc90fdccf96fa1ef628d49d1b2331a5d16bd0d15fed7fd707e6c3b254850739b6fde457a78

        ValueTransferTransactionRequest request = new ValueTransferTransactionRequest();
        String kasAddr = wallet.getAccountList().getItems().get(0).getAddress();
        request.setFrom(kasAddr);
        request.setTo(to);
        request.setValue(hexValue);
        request.setSubmit(true);

        TransactionResult result = caver.wallet.getWalletAPI().requestValueTransfer(request);
        logger.debug("send KLAY result : {}", result);

        return result;
    }

    public TransactionReceipt.TransactionReceiptData sendContract(String symbol, String contract, String to, BigDecimal amount)
            throws IOException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException, InstantiationException, IllegalAccessException, TransactionException, ApiException {
        CaverExtKAS caver = this.getKasClient().getClient();
        Wallet wallet = caver.wallet.getWalletAPI();
//        TokenMetaTable.Meta meta = tmService.getTokenMeta(symbol);
        // TODO: make Exception
//        if (meta == null)
//            return new TransactionReceipt.TransactionReceiptData();
//        if (BlockChainPlatformEnum.KLAYTN_KIP7_TOKEN.equals(meta.getBctxType()))
//            return new TransactionReceipt.TransactionReceiptData();
//        String contract = meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString();
        KIP7 kip7 = new KIP7(caver, contract);
        SendOptions sendParam = new SendOptions();

        String kasAddr = wallet.getAccountList().getItems().get(0).getAddress();
        sendParam.setFrom(kasAddr);

        String _amount = Utils.convertToPeb(amount, Utils.KlayUnit.KLAY);
        BigInteger intAmount = new BigDecimal(_amount).toBigInteger();

        TransactionReceipt.TransactionReceiptData receipt = kip7.transfer(to, intAmount, sendParam);
        logger.debug("send KIP7 result : {}", receipt);
        return receipt;
    }
}
