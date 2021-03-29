package io.talken.dex.api.service.bc;

import com.klaytn.caver.methods.response.Account;
import com.klaytn.caver.methods.response.Transaction;
import com.klaytn.caver.methods.response.TransactionReceipt;
import io.talken.common.exception.common.GeneralException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.service.blockchain.klaytn.KlaytnNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import xyz.groundx.caver_ext_kas.kas.tokenhistory.TokenHistoryQueryOptions;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.ApiResponse;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.api.TokenHistoryApi;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.model.PageableTransfers;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.model.TransferArray;

import java.math.BigInteger;

@Service
@Scope("singleton")
public class KlaytnInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(KlaytnInfoService.class);

	@Autowired
	private KlaytnNetworkService klayNetworkService;

    @Autowired
    private DSLContext dslContext;

    /****************
     * TODO : make RPC Call Error response (also eth)
     ****************/

    /**
     * get klay account
     *
     * @param address
     * @return
     * @throws GeneralException
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
	 * @param address
	 * @return
	 * @throws GeneralException
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
     * @return
     * @throws GeneralException
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
     * @param hash
     * @return
     * @throws GeneralException
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
     * @param hash
     * @return
     * @throws GeneralException
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
	 * @param contract
	 * @param address
	 * @return
	 * @throws GeneralException
	 */
	public BigInteger getErc20Balance(String contract, String address) throws GeneralException {
        try {
            return klayNetworkService.getKasClient().getClient().rpc.klay.getBalance(address).send().getValue();
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
	}

	public TransferArray getTransactionList(String address) throws GeneralException {
        String xChainId = klayNetworkService.getKasClient().getChainId();
        TokenHistoryApi thApi = klayNetworkService.getKasClient().getClient().kas.tokenHistory.getTokenHistoryApi();

        try {
            String kind = TokenHistoryQueryOptions.KIND.getAllKind().replace("'", "").replace(" ", "");
            // TODO : param need to decodeURL
            ApiResponse<PageableTransfers> resp = thApi.getTransfersByEoaWithHttpInfo(xChainId, address, kind, null, null, null, null);
            TransferArray items = resp.getData().getItems();
            return items;
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
