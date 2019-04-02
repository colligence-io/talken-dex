package io.talken.dex.api.controller;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.exception.DexException;
import io.talken.dex.api.controller.dto.AssetConvertRequest;
import io.talken.dex.api.controller.dto.AssetExchangeRequest;
import io.talken.dex.api.controller.dto.TxListRequest;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.service.AssetConvertService;
import io.talken.dex.api.controller.dto.TaskTransactionResult;
import io.talken.dex.api.service.TaskTransactionListService;
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
	public DexResponse<Double> convert(@RequestBody AssetConvertRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(assetConvertService.convert(postBody.getFrom(), postBody.getAmount(), postBody.getTo()));
	}

	@RequestMapping(value = RequestMappings.EXCHANGE_ASSET, method = RequestMethod.POST)
	public DexResponse<Double> exchange(@RequestBody AssetExchangeRequest postBody) throws DexException {
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
