package io.talken.dex.api.service.bc;

import com.klaytn.caver.contract.SendOptions;
import com.klaytn.caver.kct.kip7.KIP7;
import com.klaytn.caver.methods.response.Account;
import com.klaytn.caver.methods.response.Transaction;
import com.klaytn.caver.methods.response.TransactionReceipt;
import com.klaytn.caver.utils.Utils;
import io.talken.common.exception.common.GeneralException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.KlayTransactionListRequest;
import io.talken.dex.api.service.TokenMetaApiService;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.service.blockchain.klaytn.Kip7ContractInfoService;
import io.talken.dex.shared.service.blockchain.klaytn.KlaytnNetworkService;
import io.talken.dex.shared.service.integration.wallet.TalkenWalletService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.utils.Numeric;
import xyz.groundx.caver_ext_kas.CaverExtKAS;
import xyz.groundx.caver_ext_kas.kas.tokenhistory.TokenHistoryQueryOptions;
import xyz.groundx.caver_ext_kas.kas.wallet.Wallet;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.ApiException;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.model.PageableTransfers;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.model.TransferArray;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.wallet.model.TransactionResult;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.wallet.model.ValueTransferTransactionRequest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The type Klaytn info service.
 */
@Service
@Scope("singleton")
@RequiredArgsConstructor
public class KlaytnInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(KlaytnInfoService.class);

	private final KlaytnNetworkService klayNetworkService;

    private final Kip7ContractInfoService kip7ContractInfoService;

    private final DSLContext dslContext;

    private final TokenMetaApiService tmService;

    private final TalkenWalletService pwService;

    /**
     * The constant PAGE.
     */
    final static long PAGE = 10;

    final static String KLAY = "KLAY";

    /****************
     * TODO : make RPC Call Error response (also eth)
     ****************/

    /**
     * get klay account
     *
     * @param address the address
     * @return account account
     * @throws GeneralException the general exception
     */
    public Account.AccountData getAccount(String address) throws GeneralException {
        try {
            // TODO : sample for RPC call getError
//            Account a = klayNetworkService.getKasClient().getClient().rpc.klay.getAccount(address).send();
//            Response.Error e = a.getError();
//            if (e != null) {
//                JsonNode node = null;
//                if (e.getData() != null) {
//                    ObjectMapper mapper = new ObjectMapper();
//                    node = mapper.readTree(e.getData());
//                }
//                ErrorMessage em = new ErrorMessage(e.getCode(), e.getMessage(), node);
//                throw new JsonRpcException(em);
//            }
            return klayNetworkService.getKasClient().getClient().rpc.klay.getAccount(address).send().getResult();
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * get klay balance
     *
     * @param address the address
     * @return balance balance
     * @throws GeneralException the general exception
     */
    public BigInteger getBalance(String address) throws GeneralException{
	    try {
            return klayNetworkService.getKasClient().getClient().rpc.klay.getBalance(address).send().getValue();
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
	}

    /**
     * get klay gasPrice
     *
     * @return gas price
     * @throws GeneralException the general exception
     */
    public BigInteger getGasPrice() throws GeneralException{
        try {
            return klayNetworkService.getKasClient().getClient().rpc.klay.getGasPrice().send().getValue();
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * get klay transaction
     *
     * @param hash the hash
     * @return transaction by hash
     * @throws GeneralException the general exception
     */
    public Transaction.TransactionData getTransactionByHash(String hash) throws GeneralException{
        try {
            return klayNetworkService.getKasClient().getClient().rpc.klay.getTransactionByHash(hash).send().getResult();
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * get klay transactionReceipt
     *
     * @param hash the hash
     * @return transaction receipt by hash
     * @throws GeneralException the general exception
     */
    public TransactionReceipt.TransactionReceiptData getTransactionReceiptByHash(String hash) throws GeneralException{
        try {
            return klayNetworkService.getKasClient().getClient().rpc.klay.getTransactionReceipt(hash).send().getResult();
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * get kip7/erc20 balance
     *
     * @param contract the contract
     * @param address  the address
     * @return kip 7 balance
     * @throws GeneralException the general exception
     */
    public BigInteger getKip7Balance(String contract, String address) throws GeneralException {
        try {
            return kip7ContractInfoService.getBalanceOf(klayNetworkService.getKasClient().getClient(), contract, address);
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
	}

    /**
     * Gets transaction list.
     *
     * @param request the request
     * @return the transaction list
     * @throws GeneralException the general exception
     */
    public TransferArray getTransactionList(KlayTransactionListRequest request) throws GeneralException {
        try {
            TokenHistoryQueryOptions options = new TokenHistoryQueryOptions();

            options.setSize(PAGE);

            if (request.getContract() != null) {
                options.setCaFilter(request.getContract());
                options.setKind(TokenHistoryQueryOptions.KIND.valueOf(request.getType()));
            } else {
                options.setKind(TokenHistoryQueryOptions.KIND.valueOf(request.getType()));
            }

            if (request.getCursor() != null) {
                options.setCursor(request.getCursor());
            }

            PageableTransfers pt = klayNetworkService.getKasClient().getClient().kas.tokenHistory.getTransferHistoryByAccount(request.getAddress(), options);
            TransferArray items = pt.getItems();

            return items;
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * Gets contract.
     *
     * @param contract the contract
     * @param address  the address
     * @return the contract
     * @throws GeneralException the general exception
     */
    public Kip7ContractInfoService.Kip7ContractInfo getContract(String contract, String address) throws GeneralException {
        try {
            return kip7ContractInfoService.getKip7ContractInfo(klayNetworkService.getKasClient().getClient(), contract);
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
    }

    public TransactionResult send(String symbol, String to, BigDecimal amount) throws ApiException {
        CaverExtKAS caver = klayNetworkService.getKasClient().getClient();

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
        CaverExtKAS caver = klayNetworkService.getKasClient().getClient();
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

//	public PendingTxListResult getPendingTransactionTxList(PendingTxListRequest request) {
//	    // TODO : add condition
//        PendingTxListResult result = new PendingTxListResult();
//        Condition cond = BCTX.STATUS.eq(BctxStatusEnum.SENT)
//                .and(BCTX.BC_REF_ID.isNotNull())
//                .and(BCTX.SYMBOL.eq(request.getSymbol()))
//                .and(BCTX.BCTX_TYPE.eq(BlockChainPlatformEnum.ETHEREUM)
//                        .or(BCTX.BCTX_TYPE.eq(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN)))
//                .and(BCTX.ADDRESS_FROM.eq(request.getAddress())
//                        .or(BCTX.ADDRESS_TO.eq(request.getAddress())));
////                                            .and(BCTX.UPDATE_TIMESTAMP.isNotNull()
////                                                    .and(BCTX.UPDATE_TIMESTAMP.le(UTCUtil.getNow().minusSeconds(30)))
////                                                    .and(BCTX.UPDATE_TIMESTAMP.gt(UTCUtil.getNow().minusDays(7))))
//
//        List<Bctx> records = dslContext.selectFrom(BCTX)
//                .where(cond)
//                .fetch().into(BCTX).into(Bctx.class);
//
//        result.setRecordCount(records.size());
//        result.setBctxs(records);
//
//	    return result;
//    }
}
