package io.talken.dex.shared.service.blockchain.stellar.opreceipt;

import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import lombok.Data;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.xdr.ClaimOfferAtom;
import org.stellar.sdk.xdr.ManageOfferSuccessResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public abstract class ManageOfferClaims {
	public static List<ClaimedOffer> parseClaimedOffers(ManageOfferSuccessResult success, StellarOpReceipt opReceipt) {
		List<ClaimedOffer> claimedOffers = new ArrayList<>();

		for(ClaimOfferAtom claimOfferAtom : success.getOffersClaimed()) {
			ClaimedOffer claimedOffer = new ClaimedOffer();
			claimedOffer.setOfferId(claimOfferAtom.getOfferID().getInt64());
			claimedOffer.setSellerAccount(KeyPair.fromXdrPublicKey(claimOfferAtom.getSellerID().getAccountID()).getAccountId());
			claimedOffer.setAssetBought(StellarOpReceipt.assetToString(claimOfferAtom.getAssetBought()));
			claimedOffer.setAmountBought(StellarConverter.rawToActual(claimOfferAtom.getAmountBought().getInt64()));
			claimedOffer.setAssetSold(StellarOpReceipt.assetToString(claimOfferAtom.getAssetSold()));
			claimedOffer.setAmountSold(StellarConverter.rawToActual(claimOfferAtom.getAmountSold().getInt64()));

			opReceipt.addInvolvedAccount(claimedOffer.getSellerAccount());
			opReceipt.addInvolvedAsset(claimedOffer.getAssetBought());
			opReceipt.addInvolvedAsset(claimedOffer.getAssetSold());

			claimedOffers.add(claimedOffer);
		}

		return claimedOffers;
	}

	@Data
	public static class ClaimedOffer {
		private Long offerId;
		private String sellerAccount;

		private String assetBought;
		private BigDecimal amountBought;

		private String assetSold;
		private BigDecimal amountSold;
	}
}
