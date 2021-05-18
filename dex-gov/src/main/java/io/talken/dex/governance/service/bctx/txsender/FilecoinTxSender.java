package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxSender;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.service.blockchain.filecoin.FilecoinNetworkService;
import io.talken.dex.shared.service.blockchain.filecoin.FilecoinRpcClient;
import io.talken.dex.shared.service.blockchain.filecoin.FilecoinSign;
import io.talken.dex.shared.service.blockchain.filecoin.FilecoinTransaction;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The type Filecoin tx sender.
 */
@Component
public class FilecoinTxSender extends TxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(FilecoinTxSender.class);

	@Autowired
	private FilecoinNetworkService filecoinNetworkService;

    @Autowired
    private DSLContext dslContext;

    // 수정 필요.
    // TODO : find Default FileCoin gasLimit
    private static final BigInteger DEFAULT_GASLIMIT = BigInteger.valueOf(78800L);
//	private static final BigInteger DEFAULT_ERC20_GASLIMIT = BigInteger.valueOf(100000L);

	private static Map<String, BigInteger> nonceCheck = new HashMap<>();

    /**
     * Instantiates a new Filecoin tx sender.
     */
    public FilecoinTxSender() {
		super(BlockChainPlatformEnum.FILECOIN);
	}

	/**
	 * send filecoin tx
	 *
	 */
	@Override
	public boolean sendTx(TokenMetaTable.Meta meta, Bctx bctx, BctxLogRecord log) throws Exception {
		FilecoinRpcClient client = filecoinNetworkService.getClient();

		// nonce 가져오기
		BigInteger nonce = client.getNonce(bctx.getAddressFrom());
		BigDecimal balance = client.getbalance(bctx.getAddressFrom());

//		if (balance.compareTo (amount.add (new BigDecimal ( "0.001"))) <0) {
//			return new WalletReturnDto (LangString.CREATE_TX_FAIL, false, 200004, "", BigDecimal.ZERO, BigDecimal.ZERO);
//		}
//
//		BigDecimal pow = BigDecimal.TEN.pow (6 );
//		BigDecimal gasLimit = nonce.compareTo (BigInteger.ZERO) == 0? new BigDecimal (3) .multiply (pow)
//				: new BigDecimal (2) .multiply (pow);
//		BigDecimal fee = new BigDecimal ( "0.001");

        // TODO : 20210202 generate values static or get network default
		FilecoinTransaction tran = new FilecoinTransaction();
		tran.setFrom(bctx.getAddressFrom());
		tran.setTo(bctx.getAddressTo());
		// ???
		tran.setGasFeeCap("101183");
		// ???
		tran.setGasLimit(DEFAULT_GASLIMIT);
		tran.setNonce(nonce);
		// ???
		tran.setValue(bctx.getAmount().toBigInteger().toString());
		// ???
		tran.setGasPremium("100129");
		tran.setParams("");
		tran.setMethod(0L);

		// TODO : 20210202 if need synchronized
		log.setRequest(JSONWriter.toJsonString(tran));
		String signData = FilecoinSign.signTransaction(tran, new ArrayList<String>());
		FilecoinTransaction transaction = client.push(signData);
		log.setResponse(JSONWriter.toJsonString(transaction));

		if (transaction != null && !"".equals(transaction.getCID())) {
		    // TODO : 20210202 if dont need remove nonceCheck
			nonceCheck.put(bctx.getAddressFrom(), nonce);
			log.setBcRefId(transaction.getCID());
			log.setResponse(JSONWriter.toJsonString(transaction));
			log.store();
			return true;
		} else {
			log.setStatus(BctxStatusEnum.FAILED);
			// error code 정의된것들 목록 확인 필요 ????
			log.setErrorcode("CONTRACT_ID_NOT_MATCH");
			log.setErrormessage("filecoin send fail.");
			log.store();
			return false;
		}
	}
}
