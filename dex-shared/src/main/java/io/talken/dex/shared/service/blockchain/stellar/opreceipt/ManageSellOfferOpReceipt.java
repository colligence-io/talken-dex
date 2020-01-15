package io.talken.dex.shared.service.blockchain.stellar.opreceipt;

import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.stellar.sdk.ManageSellOfferOperation;
import org.stellar.sdk.xdr.ManageSellOfferResult;
import org.stellar.sdk.xdr.ManageSellOfferResultCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ManageSellOfferOpReceipt extends StellarOpReceipt<ManageSellOfferOperation, ManageSellOfferResult> {

	private ManageSellOfferResultCode resultCode;
	private List<ManageOfferClaims.ClaimedOffer> claimedOffers;

	@Override
	protected void parse(ManageSellOfferOperation op, ManageSellOfferResult result) {

		this.resultCode = result.getDiscriminant();
		if(this.resultCode.equals(ManageSellOfferResultCode.MANAGE_SELL_OFFER_SUCCESS)) {
			claimedOffers = ManageOfferClaims.parseClaimedOffers(result.getSuccess(), this);
		}
	}
}
