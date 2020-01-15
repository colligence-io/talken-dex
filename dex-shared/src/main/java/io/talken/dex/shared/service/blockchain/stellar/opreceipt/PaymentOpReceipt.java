package io.talken.dex.shared.service.blockchain.stellar.opreceipt;

import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.stellar.sdk.AssetTypeCreditAlphaNum;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.xdr.PaymentResult;
import org.stellar.sdk.xdr.PaymentResultCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentOpReceipt extends StellarOpReceipt<PaymentOperation, PaymentResult> {
	private String from;
	private String to;
	private BigDecimal amount;
	private String asset;
	private String assetCode;
	private String assetIssuer;
	private PaymentResultCode resultCode;

	@Override
	protected void parse(PaymentOperation op, PaymentResult result) {
		this.resultCode = result.getDiscriminant();

		this.from = (op.getSourceAccount() != null) ? op.getSourceAccount() : getSourceAccount();
		this.to = op.getDestination();
		this.amount = StellarConverter.scale(new BigDecimal(op.getAmount())).stripTrailingZeros();

		this.asset = assetToString(op.getAsset());
		if(op.getAsset() instanceof AssetTypeNative) {
			this.assetCode = "XLM";
			this.assetIssuer = null;
		} else {
			AssetTypeCreditAlphaNum asset = ((AssetTypeCreditAlphaNum) op.getAsset());
			this.assetCode = asset.getCode();
			this.assetIssuer = asset.getIssuer();
		}

		addInvolvedAsset(this.asset);
		addInvolvedAccount(this.from);
		addInvolvedAccount(this.to);
	}
}
