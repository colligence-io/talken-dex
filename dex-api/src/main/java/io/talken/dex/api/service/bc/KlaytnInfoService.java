package io.talken.dex.api.service.bc;

import com.klaytn.caver.methods.response.Account;
import com.klaytn.caver.methods.response.Transaction;
import com.klaytn.caver.methods.response.TransactionReceipt;
import io.talken.common.exception.common.GeneralException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.KlayTransactionListRequest;
import io.talken.dex.shared.service.blockchain.klaytn.Kip7ContractInfoService;
import io.talken.dex.shared.service.blockchain.klaytn.KlaytnNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import xyz.groundx.caver_ext_kas.kas.tokenhistory.TokenHistoryQueryOptions;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.model.PageableTransfers;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.model.TransferArray;

import java.math.BigInteger;

/**
 * The type Klaytn info service.
 */
@Service
@Scope("singleton")
public class KlaytnInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(KlaytnInfoService.class);

	@Autowired
	private KlaytnNetworkService klayNetworkService;

    @Autowired
    private Kip7ContractInfoService kip7ContractInfoService;

    @Autowired
    private DSLContext dslContext;

    /**
     * The constant PAGE.
     */
    final static long PAGE = 10;

    /****************
     * TODO : make RPC Call Error response (also eth)
     ****************/

    /**
     * get klay account
     *
     * @param address the address
     * @return account
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
     * @return balance
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
