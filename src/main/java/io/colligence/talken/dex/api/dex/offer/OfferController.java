package io.colligence.talken.dex.api.dex.offer;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.DTOValidator;
import io.colligence.talken.dex.api.DexResponse;
import io.colligence.talken.dex.api.RequestMappings;
import io.colligence.talken.dex.api.dex.offer.dto.CreateOfferRequest;
import io.colligence.talken.dex.api.dex.offer.dto.CreateOfferResult;
import io.colligence.talken.dex.api.dex.offer.dto.DeleteOfferRequest;
import io.colligence.talken.dex.api.dex.offer.dto.DeleteOfferResult;
import io.colligence.talken.dex.config.auth.AuthInfo;
import io.colligence.talken.dex.config.auth.AuthRequired;
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
		return DexResponse.buildResponse(offerService.createOffer(authInfo.getUserId(), postBody.getSourceAccountId(), postBody.getSellAssetCode(), postBody.getSellAssetAmount(), postBody.getBuyAssetCode(), postBody.getSellAssetPrice(), postBody.getFeeByCtx()));
	}

//  NOT AVAILABLE YET
//	@AuthRequired
//	@RequestMapping(value = RequestMappings.CREATE_PASSIVE_OFFER, method = RequestMethod.POST)
//	public DexResponse<CreatePassiveOfferResult> createPassiveOffer(@RequestBody CreatePassiveOfferRequest postBody) throws DexException {
//		DTOValidator.validate(postBody);
//		return DexResponse.buildResponse(offerService.createPassiveOffer(authInfo.getUserId(), postBody.getSourceAccountId(), postBody.getSellAssetCode(), postBody.getSellAssetAmount(), postBody.getBuyAssetCode(), postBody.getSellAssetPrice(), postBody.getFeeByCtx()));
//	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.DELETE_OFFER, method = RequestMethod.POST)
	public DexResponse<DeleteOfferResult> deleteOffer(@RequestBody DeleteOfferRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerService.deleteOffer(authInfo.getUserId(), postBody.getOfferId(), postBody.getSourceAccountId(), postBody.getSellAssetCode(), postBody.getBuyAssetCode(), postBody.getSellAssetPrice()));
	}
}
