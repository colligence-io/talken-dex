package io.talken.dex.api.service.bc;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.LuniverseGasPriceResult;
import io.talken.dex.api.controller.dto.LuniverseTxListResult;
import io.talken.dex.shared.exception.InternalServerErrorException;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumTransferReceipt;
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

/**
 * The type Luniverse info service.
 */
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
	}

    /**
     * get luniverse gasprice and limit
     *
     * @return gas price and limit
     * @throws InternalServerErrorException the internal server error exception
     */
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

    /**
     * get luniverse tx list
     *
     * @param address         the address
     * @param contractaddress the contractaddress
     * @param direction       the direction
     * @param page            the page
     * @param offset          the offset
     * @return tx list
     */
    public LuniverseTxListResult getTxList(String address, String contractaddress, Sort.Direction direction, int page, int offset) {
		LuniverseTxListResult rtn = new LuniverseTxListResult();

		if(contractaddress != null) {
			if(contractaddress.isEmpty()) contractaddress = null;
			else contractaddress = contractaddress.toLowerCase();
		}

		address = address.toLowerCase();

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
				.with(Sort.by(direction, "timeStamp"));

		rtn.setResult(mongoTemplate.find(qry, EthereumTransferReceipt.class, "luniverse_txReceipt"));
		return rtn;
	}
}
