package io.talken.dex.shared.service.blockchain.stellar;

import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.shared.service.blockchain.stellar.opreceipt.DummyOpReceipt;
import io.talken.dex.shared.service.blockchain.stellar.opreceipt.ManageBuyOfferOpReceipt;
import io.talken.dex.shared.service.blockchain.stellar.opreceipt.ManageSellOfferOpReceipt;
import io.talken.dex.shared.service.blockchain.stellar.opreceipt.PaymentOpReceipt;
import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.OperationResult;
import org.stellar.sdk.xdr.OperationType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Data
public abstract class StellarOpReceipt<TO extends Operation, TR> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarOpReceipt.class);

	@Indexed
	private String hash;
	private Long ledger;
	private String pagingToken;
	@Indexed
	private Long timeStamp;
	private String sourceAccount;
	private Long seq;
	private BigInteger feeMax;
	private BigInteger feeCharged;

	@Indexed
	private String memo;
	@Indexed
	private String taskId;
	@Indexed
	private OperationType operationType;

	@Indexed
	private List<String> involvedAccounts = new ArrayList<>();
	@Indexed
	private List<String> involvedAssets = new ArrayList<>();

	public StellarOpReceipt() { }

	public static StellarOpReceipt fromResponse(TransactionResponse response, Operation operation, OperationResult result) {
		switch(result.getTr().getDiscriminant()) {
			case PAYMENT: {
				PaymentOpReceipt rcpt = new PaymentOpReceipt();
				rcpt.setOperationType(result.getTr().getDiscriminant());
				rcpt.startImport(response, (PaymentOperation) operation, result.getTr().getPaymentResult());
				return rcpt;
			}
			case MANAGE_BUY_OFFER: {
				ManageBuyOfferOpReceipt rcpt = new ManageBuyOfferOpReceipt();
				rcpt.setOperationType(result.getTr().getDiscriminant());
				rcpt.startImport(response, (ManageBuyOfferOperation) operation, result.getTr().getManageBuyOfferResult());
				return rcpt;
			}
			case MANAGE_SELL_OFFER: {
				ManageSellOfferOpReceipt rcpt = new ManageSellOfferOpReceipt();
				rcpt.setOperationType(result.getTr().getDiscriminant());
				rcpt.startImport(response, (ManageSellOfferOperation) operation, result.getTr().getManageSellOfferResult());
				return rcpt;
			}
			case PATH_PAYMENT_STRICT_RECEIVE:
			case PATH_PAYMENT_STRICT_SEND:
			case CREATE_ACCOUNT:
			case CREATE_PASSIVE_SELL_OFFER:
			case SET_OPTIONS:
			case CHANGE_TRUST:
			case ALLOW_TRUST:
			case ACCOUNT_MERGE:
			case INFLATION:
			case MANAGE_DATA:
			case BUMP_SEQUENCE:
			default: {
				DummyOpReceipt rcpt = new DummyOpReceipt();
				rcpt.setOperationType(result.getTr().getDiscriminant());
				rcpt.startImport(response, operation, result);
				return rcpt;
			}
		}
	}

	abstract protected void parse(TO op, TR result);

	public void startImport(TransactionResponse response, TO operation, TR result) {
		this.hash = response.getHash();
		this.ledger = response.getLedger();
		this.pagingToken = response.getPagingToken();
		this.timeStamp = UTCUtil.toTimestamp_s(StellarConverter.toLocalDateTime(response.getCreatedAt()));
		this.sourceAccount = response.getSourceAccount();
		this.seq = response.getSourceAccountSequence();

		if(response.getMaxFee() != null) this.feeMax = (BigInteger.valueOf(response.getMaxFee()));
		this.feeCharged = BigInteger.valueOf(response.getFeeCharged());

		this.involvedAccounts.add(response.getSourceAccount());
		parse(operation, result);
	}

	public void addInvolvedAccount(String accountId) {
		if(!involvedAccounts.contains(accountId)) involvedAccounts.add(accountId);
	}

	public void addInvolvedAsset(String assetString) {
		if(!involvedAssets.contains(assetString)) involvedAssets.add(assetString);
	}

	static public String assetToString(org.stellar.sdk.xdr.Asset asset) {
		return assetToString(Asset.fromXdr(asset));
	}

	static public String assetToString(Asset asset) {
		if(asset instanceof AssetTypeNative) {
			return "native";
		} else {
			return ((AssetTypeCreditAlphaNum) asset).getCode() + ":" + ((AssetTypeCreditAlphaNum) asset).getIssuer();
		}
	}
}
