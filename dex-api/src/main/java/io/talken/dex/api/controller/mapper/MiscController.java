package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.AssetConvertRequest;
import io.talken.dex.api.controller.dto.AssetExchangeRequest;
import io.talken.dex.api.controller.dto.TaskTransactionResult;
import io.talken.dex.api.controller.dto.TxListRequest;
import io.talken.dex.api.service.AssetConvertService;
import io.talken.dex.api.service.TaskTransactionListService;
import io.talken.dex.shared.exception.AssetConvertException;
import io.talken.dex.shared.exception.DexException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * The type Misc controller.
 */
@RestController
public class MiscController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(MiscController.class);

	@Autowired
	private AuthInfo authInfo;

	@Autowired
	private AssetConvertService assetConvertService;

	@Autowired
	private TaskTransactionListService txListService;

    /**
     * convert value using trade aggregation
     *
     * @param postBody the post body
     * @return dex response
     * @throws DexException the dex exception
     */
    @RequestMapping(value = RequestMappings.CONVERT_ASSET, method = RequestMethod.POST)
	public DexResponse<BigDecimal> convert(@RequestBody AssetConvertRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		try {
			BigDecimal result = assetConvertService
					.convert(postBody.getFrom(), postBody.getAmount(), postBody.getTo())
					.setScale(7, BigDecimal.ROUND_UP);
			return DexResponse.buildResponse(result);
		} catch(AssetConvertException | TokenMetaNotFoundException | TokenMetaNotManagedException ex) {
			return DexResponse.buildResponse(BigDecimal.ZERO);
		}
	}

    /**
     * convert value using coinmarketcap data
     *
     * @param postBody the post body
     * @return dex response
     * @throws DexException the dex exception
     */
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

    /**
     * get DexTask tx list
     *
     * @param postBody the post body
     * @return dex response
     * @throws DexException the dex exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.TXLIST, method = RequestMethod.POST)
	public DexResponse<List<TaskTransactionResult>> txList(@RequestBody TxListRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(txListService.getTxList(postBody));
	}
}

