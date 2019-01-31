package io.colligence.talken.dex.api.dex.anchor;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.DTOValidator;
import io.colligence.talken.dex.api.DexResponse;
import io.colligence.talken.dex.api.RequestMappings;
import io.colligence.talken.dex.api.dex.DexKeyRequest;
import io.colligence.talken.dex.api.dex.DexKeyResult;
import io.colligence.talken.dex.api.dex.anchor.dto.AnchorRequest;
import io.colligence.talken.dex.api.dex.anchor.dto.AnchorResult;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorRequest;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorResult;
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
		return DexResponse.buildResponse(anchorService.anchor(authInfo.getUserId(), postBody.getPrivateWalletAddress(), postBody.getTradeWalletAddress(), postBody.getAssetCode(), postBody.getAmount()));
	}

//	@Deprecated
//	@AuthRequired
//	@RequestMapping(value = RequestMappings.ANCHOR_TASK + RequestMappings.SUBMIT_SUFFIX, method = RequestMethod.POST)
//	public DexResponse<AnchorSubmitResult> submitAnchor(@RequestBody AnchorSubmitRequest postBody) throws DexException {
//		DTOValidator.validate(postBody);
//		return DexResponse.buildResponse(anchorService.submitAnchorTransaction(authInfo.getUserId(), postBody.getTaskId(), postBody.getAssetCode(), postBody.getTxData()));
//	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.DEANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<DeanchorResult> deanchor(@RequestBody DeanchorRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.deanchor(authInfo.getUserId(), postBody.getPrivateWalletAddress(), postBody.getTradeWalletAddress(), postBody.getAssetCode(), postBody.getAmount(), Optional.ofNullable(postBody.getFeeByCtx()).orElse(false)));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.DEANCHOR_TASK_DEXKEY, method = RequestMethod.POST)
	public DexResponse<DexKeyResult> deanchorDexKey(@RequestBody DexKeyRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.deanchorDexKey(authInfo.getUserId(), postBody.getTaskId(), postBody.getTransId(), postBody.getSignature()));
	}

//	@AuthRequired
//	@RequestMapping(value = RequestMappings.DEANCHOR_TASK + RequestMappings.SUBMIT_SUFFIX, method = RequestMethod.POST)
//	public DexResponse<DeanchorSubmitResult> deanchor(@RequestBody DeanchorSubmitRequest postBody) throws DexException {
//		DTOValidator.validate(postBody);
//		return DexResponse.buildResponse(anchorService.submitDeanchorTransaction(authInfo.getUserId(), postBody.getTaskId(), postBody.getTxHash(), postBody.getTxEnvelope()));
//	}
}
