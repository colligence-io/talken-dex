package io.talken.dex.api.service.bc;

import io.talken.dex.api.controller.dto.LuniverseGasPriceResult;
import io.talken.dex.shared.exception.InternalServerErrorException;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
public class LuniverseInfoService {

	@Autowired
	private LuniverseNetworkService luniverseNetworkService;

	private Web3j rpcClient;

	@PostConstruct
	private void init() throws Exception {
		this.rpcClient = luniverseNetworkService.newMainRpcClient();
	}

	public LuniverseGasPriceResult getGasPriceAndLimit() throws InternalServerErrorException {
		try {
			LuniverseGasPriceResult rtn = new LuniverseGasPriceResult();
			rtn.setGasPrice(luniverseNetworkService.getGasPrice(this.rpcClient));
			rtn.setGasLimit(luniverseNetworkService.getGasLimit(this.rpcClient));
			return rtn;
		} catch(Exception ex) {
			throw new InternalServerErrorException(ex);
		}
	}
}
