package io.colligence.talken.dex.api.dex.anchor;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.DTOValidator;
import io.colligence.talken.dex.api.DexResponse;
import io.colligence.talken.dex.api.RequestMappings;
import io.colligence.talken.dex.api.dex.anchor.dto.*;
import io.colligence.talken.dex.config.auth.AuthRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class AnchorController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorController.class);

	@Autowired
	private AnchorService anchorService;

	@AuthRequired
	@RequestMapping(value = RequestMappings.ANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<AnchorResult> anchor(@RequestBody AnchorRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.buildAnchorRequestInformation(postBody.getPrivateWalletAddress(), postBody.getTradeWalletAddress(), postBody.getAssetCode(), postBody.getAmount()));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.ANCHOR_TASK + RequestMappings.SUBMIT_SUFFIX, method = RequestMethod.POST)
	public DexResponse<AnchorSubmitResult> submitAnchor(@RequestBody AnchorSubmitRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.submitAnchorTransaction(postBody.getAssetCode(), postBody.getTaskId(), postBody.getTxData()));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.DEANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<DeanchorResult> deanchor(@RequestBody DeanchorRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.buildDeanchorRequestInformation(postBody.getPrivateWalletAddress(), postBody.getTradeWalletAddress(), postBody.getAssetCode(), postBody.getAmount(), Optional.ofNullable(postBody.getFeeByCtx()).orElse(false)));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.DEANCHOR_TASK + RequestMappings.SUBMIT_SUFFIX, method = RequestMethod.POST)
	public DexResponse<DeanchorSubmitResult> deanchor(@RequestBody DeanchorSubmitRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.submitDeanchorTransaction(postBody.getTaskId(), postBody.getTxHash(), postBody.getTxEnvelope()));
	}
}
