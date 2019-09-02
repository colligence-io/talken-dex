package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.AssetConvertService;
import io.talken.dex.api.service.TaskTransactionListService;
import io.talken.dex.api.service.bc.LuniverseInfoService;
import io.talken.dex.shared.exception.AssetConvertException;
import io.talken.dex.shared.exception.DexException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class MiscController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(MiscController.class);

	@Autowired
	private AuthInfo authInfo;

	@Autowired
	private AssetConvertService assetConvertService;

	@Autowired
	private LuniverseInfoService luniverseInfoService;

	@Autowired
	private TaskTransactionListService txListService;

	@RequestMapping(value = RequestMappings.CONVERT_ASSET, method = RequestMethod.POST)
	public DexResponse<BigDecimal> convert(@RequestBody AssetConvertRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		try {
			BigDecimal result = assetConvertService
					.convert(postBody.getFrom(), postBody.getAmount(), postBody.getTo())
					.setScale(7, BigDecimal.ROUND_UP);
			return DexResponse.buildResponse(result);
		} catch(AssetConvertException | TokenMetaNotFoundException ex) {
			return DexResponse.buildResponse(BigDecimal.ZERO);
		}
	}

	@RequestMapping(value = RequestMappings.EXCHANGE_ASSET, method = RequestMethod.POST)
	public DexResponse<BigDecimal> exchange(@RequestBody AssetExchangeRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		try {
			BigDecimal result = assetConvertService
					.exchange(postBody.getFrom(), postBody.getAmount(), postBody.getTo())
					.setScale(7, BigDecimal.ROUND_UP);
			return DexResponse.buildResponse(result);
		} catch(AssetConvertException | TokenMetaNotFoundException ex) {
			return DexResponse.buildResponse(BigDecimal.ZERO);
		}
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.TXLIST, method = RequestMethod.POST)
	public DexResponse<List<TaskTransactionResult>> txList(@RequestBody TxListRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(txListService.getTxList(postBody));
	}

	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_LUNIVERSE_GASPRICE, method = RequestMethod.GET)
	public DexResponse<LuniverseGasPriceResult> luniverseGasPrice() throws DexException {
		return DexResponse.buildResponse(luniverseInfoService.getGasPriceAndLimit());
	}

	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_LUNIVERSE_TXLIST, method = RequestMethod.GET)
	public DexResponse<LuniverseTxListResult> luniverseTxList(
			@RequestParam("address")
					String address,
			@RequestParam(value = "contractaddress", required = false)
					String contractaddress,
			@RequestParam(value = "sort", defaultValue = "desc")
					String sort,
			@RequestParam(value = "page", defaultValue = "1")
					Integer page,
			@RequestParam(value = "offset", defaultValue = "10")
					Integer offset
	) throws DexException {
		Sort.Direction direction;
		if(sort == null || !sort.equalsIgnoreCase("asc")) direction = Sort.Direction.DESC;
		else direction = Sort.Direction.ASC;
		if(page == null) page = 1;
		if(offset == null) offset = 10;

		return DexResponse.buildResponse(luniverseInfoService.getTxList(address, contractaddress, direction, page, offset));
	}
}

