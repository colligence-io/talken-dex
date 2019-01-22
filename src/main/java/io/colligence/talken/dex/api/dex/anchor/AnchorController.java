package io.colligence.talken.dex.api.dex.anchor;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.DTOValidator;
import io.colligence.talken.dex.api.DexResponse;
import io.colligence.talken.dex.api.RequestMappings;
import io.colligence.talken.dex.api.dex.anchor.dto.*;
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

	@RequestMapping(value = RequestMappings.ANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<AnchorResult> anchor(@RequestBody AnchorRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.buildAnchorRequestInformation(postBody.getPrivateWalletAddress(), postBody.getTradeWalletAddress(), postBody.getAssetCode(), postBody.getAmount()));
	}

	@RequestMapping(value = RequestMappings.ANCHOR_TASK + RequestMappings.SUBMIT_SUFFIX, method = RequestMethod.POST)
	public DexResponse<AnchorSubmitResult> submitAnchor(@RequestBody AnchorSubmitRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.submitAnchorTransaction(postBody.getTaskID(), postBody.getTxHash(), postBody.getTxEnvelope()));
	}

	@RequestMapping(value = RequestMappings.DEANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<DeanchorResult> deanchor(@RequestBody DeanchorRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.buildDeanchorRequestInformation());
	}

	@RequestMapping(value = RequestMappings.DEANCHOR_TASK + RequestMappings.SUBMIT_SUFFIX, method = RequestMethod.POST)
	public DexResponse<DeanchorSubmitResult> deanchor(@RequestBody DeanchorSubmitRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.submitDeanchorTransaction(postBody.getTaskID(), postBody.getTxHash(), postBody.getTxEnvelope()));
	}
}
