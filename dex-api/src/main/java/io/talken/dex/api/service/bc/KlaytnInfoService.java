package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.PendingTxListRequest;
import io.talken.dex.api.controller.dto.PendingTxListResult;
import io.talken.dex.shared.service.blockchain.klaytn.KlaytnNetworkService;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;

import static io.talken.common.persistence.jooq.Tables.BCTX;

@Service
@Scope("singleton")
public class KlaytnInfoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(KlaytnInfoService.class);

	@Autowired
	private KlaytnNetworkService klayNetworkService;

    @Autowired
    private DSLContext dslContext;

	/**
	 * get klay balance
	 *
	 * @param address
	 * @return
	 * @throws GeneralException
	 */
	public BigInteger getBalance(String address) throws GeneralException{
	    try {
            BigInteger balance = klayNetworkService.getKasClient().getClient().rpc.klay.getBalance(address).send().getValue();
            return balance;
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
            BigInteger balance = klayNetworkService.getKasClient().getClient().rpc.klay.getBalance(address).send().getValue();
            return balance;
        } catch(Exception ex) {
            throw new GeneralException(ex);
        }
	}

	public PendingTxListResult getPendingTransactionTxList(PendingTxListRequest request) {
	    // TODO : add condition
        PendingTxListResult result = new PendingTxListResult();
        Condition cond = BCTX.STATUS.eq(BctxStatusEnum.SENT)
                .and(BCTX.BC_REF_ID.isNotNull())
                .and(BCTX.SYMBOL.eq(request.getSymbol()))
                .and(BCTX.BCTX_TYPE.eq(BlockChainPlatformEnum.ETHEREUM)
                        .or(BCTX.BCTX_TYPE.eq(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN)))
                .and(BCTX.ADDRESS_FROM.eq(request.getAddress())
                        .or(BCTX.ADDRESS_TO.eq(request.getAddress())));
//                                            .and(BCTX.UPDATE_TIMESTAMP.isNotNull()
//                                                    .and(BCTX.UPDATE_TIMESTAMP.le(UTCUtil.getNow().minusSeconds(30)))
//                                                    .and(BCTX.UPDATE_TIMESTAMP.gt(UTCUtil.getNow().minusDays(7))))

        List<Bctx> records = dslContext.selectFrom(BCTX)
                .where(cond)
                .fetch().into(BCTX).into(Bctx.class);

        result.setRecordCount(records.size());
        result.setBctxs(records);

	    return result;
    }

//    public EthTransactionResultDTO getTransaction(String txHash) throws ExecutionException, InterruptedException {
//        EthTransactionResultDTO.EthTransactionResultDTOBuilder builder = EthTransactionResultDTO.builder();
//        if (txHash != null) {
//            Transaction tx = ethNetworkService.getEthTransaction(txHash);
//            if (tx != null) {
//                builder.blockHash(tx.getBlockHash())
//                        .blockNumber(tx.getBlockNumberRaw())
//                        .from(tx.getFrom())
//                        .gas(tx.getGasRaw())
//                        .gasPrice(tx.getGasPriceRaw())
//                        .hash(tx.getHash())
//                        .input(tx.getInput())
//                        .nonce(tx.getNonceRaw())
//                        .to(tx.getTo())
//                        .transactionIndex(tx.getTransactionIndexRaw())
//                        .value(tx.getValueRaw())
//                        .v(tx.getV())
//                        .s(tx.getS())
//                        .r(tx.getR());
//
//                if (tx.getCreates() != null) builder.creates(tx.getCreates());
//                if (tx.getChainId() != null) builder.chainId(tx.getChainId());
//                if (tx.getPublicKey() != null) builder.publicKey(tx.getPublicKey());
//            }
//        }
//
//        return builder.build();
//    }
//
//    public EthTransactionReceiptResultDTO getTransactionReceipt(String txHash) throws ExecutionException, InterruptedException {
//        EthTransactionReceiptResultDTO.EthTransactionReceiptResultDTOBuilder builder = EthTransactionReceiptResultDTO.builder();
//        if (txHash != null) {
//            TransactionReceipt tx = ethNetworkService.getEthTransactionReceipt(txHash);
//            if (tx != null) {
//                builder.transactionHash(tx.getTransactionHash())
//                        .transactionIndex(tx.getTransactionIndex())
//                        .blockHash(tx.getBlockHash())
//                        .blockNumber(tx.getBlockNumberRaw())
//                        .from(tx.getFrom())
//                        .to(tx.getTo())
//                        .cumulativeGasUsed(tx.getCumulativeGasUsed())
//                        .gasUsed(tx.getGasUsed())
//                        .contractAddress(tx.getContractAddress())
//                        .logs(tx.getLogs())
//                        .logsBloom(tx.getLogsBloom())
//                        .root(tx.getRoot())
//                        .status(tx.getStatus());
//
//                builder.revertReason(tx.getRevertReason());
//                builder.isStatus(tx.isStatusOK());
//            }
//        }
//
//        return builder.build();
//    }
}
