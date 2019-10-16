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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OfferController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(OfferController.class);

	@Autowired
	private OfferService offerService;

	@Autowired
	private FeeCalculationService feeCalculationService;

	@Autowired
	private AuthInfo authInfo;

	// sell
	@AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_SELL_FEE, method = RequestMethod.POST)
	public DexResponse<CalculateFeeResult> calculateSellOfferFee(@RequestBody CreateOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(feeCalculationService.calculateOfferFee(true, postBody.getSellAssetCode(), postBody.getBuyAssetCode(), postBody.getAmount(), postBody.getPrice(), postBody.getFeeByTalk()));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_SELL_CREATE_TASK, method = RequestMethod.POST)
	public DexResponse<CreateOfferResult> createSellOffer(@RequestBody CreateOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.createSellOffer(authInfo.getUser(), postBody));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_SELL_DELETE_TASK, method = RequestMethod.POST)
	public DexResponse<DeleteOfferResult> deleteSellOffer(@RequestBody DeleteOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.deleteSellOffer(authInfo.getUser(), postBody));
	}


	// buy
	@AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_BUY_FEE, method = RequestMethod.POST)
	public DexResponse<CalculateFeeResult> calculateBuyOfferFee(@RequestBody CreateOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(feeCalculationService.calculateOfferFee(false, postBody.getSellAssetCode(), postBody.getBuyAssetCode(), postBody.getAmount(), postBody.getPrice(), postBody.getFeeByTalk()));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_BUY_CREATE_TASK, method = RequestMethod.POST)
	public DexResponse<CreateOfferResult> createBuyOffer(@RequestBody CreateOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.createBuyOffer(authInfo.getUser(), postBody));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.OFFER_BUY_DELETE_TASK, method = RequestMethod.POST)
	public DexResponse<DeleteOfferResult> deleteBuyOffer(@RequestBody DeleteOfferRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.deleteBuyOffer(authInfo.getUser(), postBody));
	}
}
