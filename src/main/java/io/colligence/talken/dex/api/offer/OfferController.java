package io.colligence.talken.dex.api.offer;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.DTOValidator;
import io.colligence.talken.dex.api.DexResponse;
import io.colligence.talken.dex.api.TxInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OfferController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(OfferController.class);

	@Autowired
	private OfferTxBuilderService offerTxBuilderService;

	@RequestMapping(value = "/offer", method = RequestMethod.POST)
	public DexResponse<TxInformation> offer(@RequestBody OfferRequestDTO postBody) throws Exception {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(offerTxBuilderService.buildOfferTx(postBody.getSourceAccountId(), postBody.getSellAssetCode(), postBody.getSellAssetAmount(), postBody.getBuyAssetCode(), postBody.getSellAssetPrice()));
	}
}
