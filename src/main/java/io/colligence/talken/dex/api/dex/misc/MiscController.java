package io.colligence.talken.dex.api.dex.misc;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.DTOValidator;
import io.colligence.talken.dex.api.DexResponse;
import io.colligence.talken.dex.api.RequestMappings;
import io.colligence.talken.dex.api.dex.AssetConvertService;
import io.colligence.talken.dex.api.dex.misc.dto.AssetConvertRequest;
import io.colligence.talken.dex.api.dex.misc.dto.AssetExchangeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MiscController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(MiscController.class);
//
//	@Autowired
//	private AuthInfo authInfo;

	@Autowired
	private AssetConvertService assetConvertService;

	@RequestMapping(value = RequestMappings.CONVERT_ASSET, method = RequestMethod.POST)
	public DexResponse<Double> anchor(@RequestBody AssetConvertRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(assetConvertService.convertAsset(postBody.getFrom(), postBody.getAmount(), postBody.getTo()));
	}

	@RequestMapping(value = RequestMappings.EXCHANGE_ASSET, method = RequestMethod.POST)
	public DexResponse<Double> anchor(@RequestBody AssetExchangeRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(assetConvertService.exchangeAssetToFiat(postBody.getFrom(), postBody.getAmount(), postBody.getTo()));
	}
}
