package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.FeeCalculationService;
import io.talken.dex.api.service.OfferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotEmpty;

/**
 * The type Offer controller.
 */
@RestController
public class OfferController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(OfferController.class);

	@Autowired
	private OfferService offerService;

	@Autowired
	private FeeCalculationService feeCalculationService;

	@Autowired
	private AuthInfo authInfo;

    /**
     * Get offer detail
     *
     * @param offerId the offer id
     * @return offer detail
     * @throws TalkenException the talken exception
     */
    @RequestMapping(value = RequestMappings.OFFER_DETAIL, method = RequestMethod.GET)
	public DexResponse<OfferDetailResult> getOfferDetail(@PathVariable @NotEmpty long offerId) throws TalkenException {
		return DexResponse.buildResponse(offerService.getOfferDetail(offerId));
	}

    /**
     * calculate Sell asset fee
     *
     * @param postBody the post body
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_SELL_FEE, method = RequestMethod.POST)
	public DexResponse<CalculateFeeResult> calculateSellOfferFee(@RequestBody CreateOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(feeCalculationService.calculateSellOfferFee(postBody.getSellAssetCode(), postBody.getAmount(), postBody.getPrice()));
	}

    /**
     * request sell asset
     *
     * @param postBody the post body
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_SELL_CREATE_TASK, method = RequestMethod.POST)
	public DexResponse<CreateOfferResult> createSellOffer(@RequestBody CreateOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.createSellOffer(authInfo.getUser(), postBody));
	}

    /**
     * cancel sell offer
     *
     * @param postBody the post body
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_SELL_DELETE_TASK, method = RequestMethod.POST)
	public DexResponse<DeleteOfferResult> deleteSellOffer(@RequestBody DeleteOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.deleteSellOffer(authInfo.getUser(), postBody));
	}


    /**
     * calculate buy offer fee
     *
     * @param postBody the post body
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_BUY_FEE, method = RequestMethod.POST)
	public DexResponse<CalculateFeeResult> calculateBuyOfferFee(@RequestBody CreateOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(feeCalculationService.calculateBuyOfferFee(postBody.getBuyAssetCode(), postBody.getAmount(), postBody.getPrice()));
	}

    /**
     * request buy offer
     *
     * @param postBody the post body
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_BUY_CREATE_TASK, method = RequestMethod.POST)
	public DexResponse<CreateOfferResult> createBuyOffer(@RequestBody CreateOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.createBuyOffer(authInfo.getUser(), postBody));
	}

    /**
     * cancel buy offer
     *
     * @param postBody the post body
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_BUY_DELETE_TASK, method = RequestMethod.POST)
	public DexResponse<DeleteOfferResult> deleteBuyOffer(@RequestBody DeleteOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.deleteBuyOffer(authInfo.getUser(), postBody));
	}
}
