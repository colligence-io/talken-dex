package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.bc.EthereumInfoService;
import io.talken.dex.api.service.bc.LuniverseInfoService;
import io.talken.dex.shared.exception.DexException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

@RestController
public class BlockChainInfoController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(BlockChainInfoController.class);

	@Autowired
	private LuniverseInfoService luniverseInfoService;

	@Autowired
	private EthereumInfoService ethereumInfoService;

	@Autowired
	private AuthInfo authInfo;

	/**
	 * get luniverse gas price and limit
	 *
	 * @return
	 * @throws DexException
	 */
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_LUNIVERSE_GASPRICE, method = RequestMethod.GET)
	public DexResponse<LuniverseGasPriceResult> luniverseGasPrice() throws DexException {
		return DexResponse.buildResponse(luniverseInfoService.getGasPriceAndLimit());
	}

	/**
	 * get luniverse tx list
	 *
	 * @param address
	 * @param contractaddress
	 * @param sort
	 * @param page
	 * @param offset
	 * @return luniverse tx list similar to etherscan.io
	 * @throws DexException
	 */
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

	/**
	 * get eth balance
	 *
	 * @param postBody
	 * @return
	 * @throws TalkenException
	 */
	@AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETETHBALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getEthBalance(@RequestBody EthBalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(ethereumInfoService.getEthBalance(postBody.getAddress()));
	}

	/**
	 * get erc20 balance
	 *
	 * @param postBody
	 * @return
	 * @throws TalkenException
	 */
	@AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETERC20BALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getErc20Balance(@RequestBody Erc20BalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(ethereumInfoService.getErc20Balance(postBody.getContract(), postBody.getAddress()));
	}

    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETPENDING_TXLIST, method = RequestMethod.POST)
    public DexResponse<PendingTxListResult> getPendingTransactionTxList(@RequestBody PendingTxListRequest request) throws Exception {
        return DexResponse.buildResponse(ethereumInfoService.getPendingTransactionTxList(request));
    }

    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETTRANSACTIONCOUNT, method = RequestMethod.GET)
    public DexResponse<Map<String, BigInteger>> getTransactionCount(@RequestParam("address") String address) throws Exception {
	    if (address != null) {
	        return DexResponse.buildResponse(ethereumInfoService.getTransactionCount(address));
        } else {
            return DexResponse.buildResponse(new HashMap<>());
        }
    }
}