package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.LuniverseGasPriceResult;
import io.talken.dex.api.controller.dto.LuniverseTxListResult;
import io.talken.dex.api.service.bc.LuniverseInfoService;
import io.talken.dex.api.service.integration.relay.RelayServerService;
import io.talken.dex.api.service.integration.relay.dto.RelayTransferDTO;
import io.talken.dex.shared.exception.DexException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlockChainInfoService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(BlockChainInfoService.class);

	@Autowired
	private LuniverseInfoService luniverseInfoService;

	@Autowired
	private RelayServerService relayServerService;

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

	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_TRANSFER_BCINFO, method = RequestMethod.GET)
	public DexResponse<RelayTransferDTO> transferBlockchainInfo(@RequestParam("symbol") String symbol) throws TalkenException {
		return DexResponse.buildResponse(relayServerService.createTransferDTObase(symbol));
	}
}

