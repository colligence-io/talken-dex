package io.talken.dex.api.service.bc;

import io.talken.common.exception.common.GeneralException;
import io.talken.dex.shared.service.blockchain.ethereum.Erc20ContractInfoService;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.math.BigInteger;

@Service
@Scope("singleton")
public class EthereumInfoService {

	@Autowired
	private EthereumNetworkService ethereumNetworkService;

	@Autowired
	private Erc20ContractInfoService contractInfoService;

	public BigInteger getEthBalance(String address) throws GeneralException {
		return getEthBalance(ethereumNetworkService.getLocalClient().newClient(), address);
	}

	public BigInteger getEthBalance(Web3j web3j, String address) throws GeneralException {
		try {
			return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
		} catch(Exception ex) {
			throw new GeneralException(ex);
		}
	}

	public BigInteger getErc20Balance(String contract, String address) throws GeneralException {
		return getErc20Balance(ethereumNetworkService.getLocalClient().newClient(), contract, address);
	}

	public BigInteger getErc20Balance(Web3j web3j, String contract, String address) throws GeneralException {
		try {
			return contractInfoService.getBalanceOf(web3j, contract, address);
		} catch(Exception ex) {
			throw new GeneralException(ex);
		}
	}
}
