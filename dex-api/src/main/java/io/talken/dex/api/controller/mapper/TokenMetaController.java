package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.ContractListResult;
import io.talken.dex.api.controller.dto.ContractRequest;
import io.talken.dex.api.controller.dto.TotalMarketCapResult;
import io.talken.dex.api.service.TokenMetaApiService;
import io.talken.dex.shared.TokenMetaTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotEmpty;

/**
 * The type Token meta controller.
 */
@RestController
public class TokenMetaController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaController.class);

	@Autowired
	private TokenMetaApiService tmService;

    @Autowired
    private AuthInfo authInfo;


    /**
     * managed meta list
     *
     * @return dex response
     */
    @RequestMapping(value = RequestMappings.TMS_MI_LIST, method = RequestMethod.GET)
	public DexResponse<TokenMetaTable> managedInfoList() {
		return DexResponse.buildResponse(tmService.getTokenMetaManagedList());
	}

    /**
     * single meta
     *
     * @param symbol the symbol
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @RequestMapping(value = RequestMappings.TMS_TM_INFO, method = RequestMethod.GET)
	public DexResponse<TokenMetaTable.Meta> tokenMeta(@PathVariable @NotEmpty String symbol) throws TalkenException {
		return DexResponse.buildResponse(tmService.getTokenMeta(symbol));
	}

    /**
     * all meta list
     *
     * @return dex response
     */
    @RequestMapping(value = RequestMappings.TMS_TM_LIST, method = RequestMethod.GET)
	public DexResponse<TokenMetaTable> tokenMetaList() {
		return DexResponse.buildResponse(tmService.getTokenMetaList());
	}

    /**
     * market cap info
     *
     * @return dex response
     */
    @RequestMapping(value = RequestMappings.TMS_TMC_INFO, method = RequestMethod.GET)
    public DexResponse<TotalMarketCapResult> totalMarketCapInfo() {
        return DexResponse.buildResponse(tmService.getTotalMarketCapInfo());
    }


    /**
     * Add contract dex response.
     *
     * @param postBody the post body
     * @return the dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.TMS_CONTRACT_ADD, method = RequestMethod.POST)
    public DexResponse<Boolean> addContract(@RequestBody ContractRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(tmService.addContract(authInfo.getUser(), postBody));
    }

    /**
     * Remove contract dex response.
     *
     * @param contractAddress the contract address
     * @return the dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.TMS_CONTRACT_REMOVE, method = RequestMethod.DELETE)
    public DexResponse<Boolean> removeContract(@PathVariable @NotEmpty String contractAddress) throws TalkenException {
        return DexResponse.buildResponse(tmService.removeContract(authInfo.getUser(), contractAddress));
    }

    /**
     * List contract dex response.
     *
     * @return the dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.TMS_CONTRACT_LIST, method = RequestMethod.GET)
    public DexResponse<ContractListResult> listContract() throws TalkenException {
        return DexResponse.buildResponse(tmService.listContract(authInfo.getUser()));
    }
}
