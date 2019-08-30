package io.talken.dex.api.service.bc;

import io.talken.common.util.PostLaunchExecutor;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.LuniverseGasPriceResult;
import io.talken.dex.api.controller.dto.LuniverseTxListResult;
import io.talken.dex.shared.exception.InternalServerErrorException;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumTxReceipt;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
public class LuniverseInfoService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseInfoService.class);

	@Autowired
	private LuniverseNetworkService luniverseNetworkService;

	@Autowired
	private MongoTemplate mongoTemplate;

	private Web3j rpcClient;

	@PostConstruct
	private void init() throws Exception {
		this.rpcClient = luniverseNetworkService.newMainRpcClient();

		PostLaunchExecutor.addTask(() -> {
			try {
				logger.logObjectAsJSON(getTxList("0x8526e732f405c742d4a078293dad3a6633b57c79", null, Sort.Direction.DESC, 1, 10));
			} catch(Exception ex) {
				logger.exception(ex);
			}
		});
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

	public LuniverseTxListResult getTxList(String address, String contractaddress, Sort.Direction direction, int page, int offset) {
		LuniverseTxListResult rtn = new LuniverseTxListResult();

		Criteria ct =
				new Criteria().andOperator(
						Criteria.where("contractAddress").is(contractaddress),
						new Criteria().orOperator(
								Criteria.where("from").is(address),
								Criteria.where("to").is(address)
						)
				);

		Query qry = new Query()
				.addCriteria(ct)
				.limit(offset)
				.skip(Math.max((page - 1), 0) * offset)
				.with(new Sort(direction, "timeStamp"));

		rtn.setResult(mongoTemplate.find(qry, EthereumTxReceipt.class, "luniverse_txReceipt"));
		return rtn;
	}
}
