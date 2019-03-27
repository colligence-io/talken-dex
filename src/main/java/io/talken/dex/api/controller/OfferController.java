package io.talken.dex.api.controller;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.dto.*;
import io.talken.dex.api.service.OfferService;
import io.talken.dex.config.auth.AuthInfo;
import io.talken.dex.config.auth.AuthRequired;
import io.talken.dex.exception.DexException;
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
	private AuthInfo authInfo;

	@AuthRequired
	@RequestMapping(value = RequestMappings.CREATE_OFFER, method = RequestMethod.POST)
	public DexResponse<CreateOfferResult> createOffer(@RequestBody CreateOfferRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.createOffer(authInfo.getUserId(), postBody.getTradeWalletAddress(), postBody.getSellAssetCode(), postBody.getSellAssetAmount(), postBody.getBuyAssetCode(), postBody.getSellAssetPrice(), postBody.getFeeByCtx()));
	}

	@RequestMapping(value = RequestMappings.CREATE_OFFER_DEXKEY, method = RequestMethod.POST)
	public DexResponse<DexKeyResult> createOfferDexKey(@RequestBody DexKeyRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.createOfferDexKey(postBody.getUserId(), postBody.getTaskId(), postBody.getTransId(), postBody.getSignature()));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.DELETE_OFFER, method = RequestMethod.POST)
	public DexResponse<DeleteOfferResult> deleteOffer(@RequestBody DeleteOfferRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.deleteOffer(authInfo.getUserId(), postBody.getOfferId(), postBody.getTradeWalletAddress(), postBody.getSellAssetCode(), postBody.getBuyAssetCode(), postBody.getSellAssetPrice()));
	}

	@RequestMapping(value = RequestMappings.DELETE_OFFER_DEXKEY, method = RequestMethod.POST)
	public DexResponse<DexKeyResult> deleteOfferDexKey(@RequestBody DexKeyRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.deleteOfferDexKey(postBody.getUserId(), postBody.getTaskId(), postBody.getTransId(), postBody.getSignature()));
	}
}
