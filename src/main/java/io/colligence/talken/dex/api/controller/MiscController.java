package io.colligence.talken.dex.api.controller;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.dto.AssetConvertRequest;
import io.colligence.talken.dex.api.dto.AssetExchangeRequest;
import io.colligence.talken.dex.api.dto.TxListRequest;
import io.colligence.talken.dex.config.auth.AuthInfo;
import io.colligence.talken.dex.config.auth.AuthRequired;
import io.colligence.talken.dex.api.service.AssetConvertService;
import io.colligence.talken.dex.api.dto.TaskTransactionResult;
import io.colligence.talken.dex.api.service.TaskTransactionListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MiscController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(MiscController.class);

	@Autowired
	private AuthInfo authInfo;

	@Autowired
	private AssetConvertService assetConvertService;

	@Autowired
	private TaskTransactionListService txListService;

	@RequestMapping(value = RequestMappings.CONVERT_ASSET, method = RequestMethod.POST)
	public DexResponse<Double> anchor(@RequestBody AssetConvertRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(assetConvertService.convert(postBody.getFrom(), postBody.getAmount(), postBody.getTo()));
	}

	@RequestMapping(value = RequestMappings.EXCHANGE_ASSET, method = RequestMethod.POST)
	public DexResponse<Double> anchor(@RequestBody AssetExchangeRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(assetConvertService.exchange(postBody.getFrom(), postBody.getAmount(), postBody.getTo()));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.TXLIST, method = RequestMethod.POST)
	public DexResponse<List<TaskTransactionResult>> txList(@RequestBody TxListRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(txListService.getTxList(postBody));
	}
}
