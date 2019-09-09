package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.AnchorService;
import io.talken.dex.api.service.FeeCalculationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class AnchorController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorController.class);

	@Autowired
	private AnchorService anchorService;

	@Autowired
	private FeeCalculationService feeService;

	@Autowired
	private AuthInfo authInfo;

	@AuthRequired
	@RequestMapping(value = RequestMappings.ANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<AnchorResult> anchor_old(@RequestBody AnchorRequestOld postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.anchor_old(authInfo.getUserId(), postBody.getFrom(), postBody.getStellar(), postBody.getSymbol(), postBody.getAmount(), postBody));
	}

	//	@AuthRequired
	//	@RequestMapping(value = RequestMappings.ANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<AnchorResult> anchor(@RequestBody AnchorRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.anchor(authInfo.getUserId(), postBody));
	}

	@RequestMapping(value = RequestMappings.ANCHOR_TASK_DEXKEY, method = RequestMethod.POST)
	public DexResponse<DexKeyResult> anchorDexKey(@RequestBody DexKeyRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.anchorDexKey(postBody.getUserId(), postBody));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.DEANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<DeanchorResult> deanchor(@RequestBody DeanchorRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.deanchor(authInfo.getUserId(), postBody));
	}

	@RequestMapping(value = RequestMappings.DEANCHOR_TASK_DEXKEY, method = RequestMethod.POST)
	public DexResponse<DexKeyResult> deanchorDexKey(@RequestBody DexKeyRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.deanchorDexKey(postBody.getUserId(), postBody));
	}

	@RequestMapping(value = RequestMappings.DEANCHOR_FEE, method = RequestMethod.POST)
	public DexResponse<CalculateFeeResult> deanchorFee(@RequestBody DeanchorRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(feeService.calculateDeanchorFee(postBody.getAssetCode(), postBody.getAmount(), postBody.getFeeByTalk()));
	}
}
