package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.SwapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class SwapController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SwapController.class);

	@Autowired
	private SwapService swapService;

	@Autowired
	private AuthInfo authInfo;

	@AuthRequired
	@RequestMapping(value = RequestMappings.SWAP_PREDICT, method = RequestMethod.POST)
	public DexResponse<SwapPredictionResult> anchor(@RequestBody SwapRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(swapService.getSwapResultPrediction(postBody.getSourceAssetCode(), postBody.getSourceAmount(), postBody.getTargetAssetCode()));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.SWAP, method = RequestMethod.POST)
	public DexResponse<SwapResult> anchorDexKey(@RequestBody SwapRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(swapService.swap(authInfo.getUserId(), postBody));
	}

	@RequestMapping(value = RequestMappings.SWAP_DEXKEY, method = RequestMethod.POST)
	public DexResponse<DexKeyResult> deanchorDexKey(@RequestBody DexKeyRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(swapService.swapDexKey(postBody.getUserId(), postBody.getTaskId(), postBody.getTransId(), postBody.getSignature()));
	}
}
