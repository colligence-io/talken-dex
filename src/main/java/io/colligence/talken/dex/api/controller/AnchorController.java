package io.colligence.talken.dex.api.controller;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.exception.DexException;
import io.colligence.talken.dex.api.dto.*;
import io.colligence.talken.dex.api.service.AnchorService;
import io.colligence.talken.dex.config.auth.AuthInfo;
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

	@Autowired
	private AuthInfo authInfo;

	@AuthRequired
	@RequestMapping(value = RequestMappings.ANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<AnchorResult> anchor(@RequestBody AnchorRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.anchor(authInfo.getUserId(), postBody.getFrom(), postBody.getStellar(), postBody.getSymbol(), postBody.getAmount(), postBody));
	}

	@RequestMapping(value = RequestMappings.ANCHOR_TASK_DEXKEY, method = RequestMethod.POST)
	public DexResponse<DexKeyResult> anchorDexKey(@RequestBody DexKeyRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.anchorDexKey(postBody.getUserId(), postBody.getTaskId(), postBody.getTransId(), postBody.getSignature()));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.DEANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<DeanchorResult> deanchor(@RequestBody DeanchorRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.deanchor(authInfo.getUserId(), postBody.getPrivateWalletAddress(), postBody.getTradeWalletAddress(), postBody.getAssetCode(), postBody.getAmount(), Optional.ofNullable(postBody.getFeeByCtx()).orElse(false)));
	}

	@RequestMapping(value = RequestMappings.DEANCHOR_TASK_DEXKEY, method = RequestMethod.POST)
	public DexResponse<DexKeyResult> deanchorDexKey(@RequestBody DexKeyRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.deanchorDexKey(postBody.getUserId(), postBody.getTaskId(), postBody.getTransId(), postBody.getSignature()));
	}
}
