package io.talken.dex.shared.service.blockchain.stellar.opreceipt;

import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.stellar.sdk.ManageBuyOfferOperation;
import org.stellar.sdk.xdr.ManageBuyOfferResult;
import org.stellar.sdk.xdr.ManageBuyOfferResultCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ManageBuyOfferOpReceipt extends StellarOpReceipt<ManageBuyOfferOperation, ManageBuyOfferResult> {

	private ManageBuyOfferResultCode resultCode;
	private List<ManageOfferClaims.ClaimedOffer> claimedOffers;

	@Override
	protected void parse(ManageBuyOfferOperation op, ManageBuyOfferResult result) {

		this.resultCode = result.getDiscriminant();
		if(this.resultCode.equals(ManageBuyOfferResultCode.MANAGE_BUY_OFFER_SUCCESS)) {
			claimedOffers = ManageOfferClaims.parseClaimedOffers(result.getSuccess(), this);
		}
	}
}
