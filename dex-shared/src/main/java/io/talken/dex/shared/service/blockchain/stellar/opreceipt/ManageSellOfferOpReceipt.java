package io.talken.dex.shared.service.blockchain.stellar.opreceipt;

import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.stellar.sdk.ManageSellOfferOperation;
import org.stellar.sdk.xdr.ManageSellOfferResult;
import org.stellar.sdk.xdr.ManageSellOfferResultCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * The type Manage sell offer op receipt.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ManageSellOfferOpReceipt extends StellarOpReceipt<ManageSellOfferOperation, ManageSellOfferResult> {

    /**
     * The type Request.
     */
    @Data
	@Builder
	public static class Request {
		private String ownerAccount;
		private String buyingAsset;
		private String sellingAsset;
		private BigDecimal amount;
		private BigDecimal price;
		private Long offerId;
	}

    /**
     * The type Result.
     */
    @Data
	@Builder
	public static class Result {
		private Long offerId;
		private ManageSellOfferResultCode resultCode;
		private List<ManageOfferClaims.ClaimedOffer> claimedOffers;
	}

	private Request request;
	private Result result;

	@Override
	protected void parse(ManageSellOfferOperation op, ManageSellOfferResult result) {

		this.request = Request.builder()
				.ownerAccount(op.getSourceAccount()!=null?op.getSourceAccount():getSourceAccount())
				.buyingAsset(assetToString(op.getBuying()))
				.sellingAsset(assetToString(op.getSelling()))
				.amount(StellarConverter.scale(new BigDecimal(op.getAmount())))
				.price(new BigDecimal(op.getPrice()))
				.offerId(op.getOfferId())
				.build();

		Result.ResultBuilder resultBuilder = Result.builder()
				.resultCode(result.getDiscriminant());

		addInvolvedAccount(this.request.getOwnerAccount());
		addInvolvedAsset(this.request.getBuyingAsset());
		addInvolvedAsset(this.request.getSellingAsset());

		if(result.getSuccess() != null && result.getSuccess().getOffer() != null && result.getSuccess().getOffer().getOffer() != null && result.getSuccess().getOffer().getOffer().getOfferID() != null) {
			resultBuilder.offerId(result.getSuccess().getOffer().getOffer().getOfferID().getInt64());
		}

		if(result.getDiscriminant().equals(ManageSellOfferResultCode.MANAGE_SELL_OFFER_SUCCESS)) {
			resultBuilder.claimedOffers(ManageOfferClaims.parseClaimedOffers(result.getSuccess(), this));
		}

		this.result = resultBuilder.build();
	}
}
