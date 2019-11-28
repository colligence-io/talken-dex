package io.talken.dex.shared.service.blockchain.ethereum;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

public class EthRpcClient {
	private final String uri;
	private final String client;
	private final boolean isParity;

	public EthRpcClient(String uri) throws IOException {
		this.uri = uri;
		this.client = newClient().web3ClientVersion().send().getWeb3ClientVersion();
		this.isParity = client.startsWith("Parity-Ethereum");
	}

	public boolean isParity() {
		return isParity;
	}

	public String getUri() {
		return uri;
	}

	public String getClientVersion() {
		return client;
	}

	public Web3j newClient() {
		return Web3j.build(newWeb3jService());
	}

	public Web3jService newWeb3jService() {
		return new Web3jHttpService(this.uri);
	}

	public BigInteger getNonce(Web3jService web3jService, String address) throws Exception {
		if(this.isParity) {
			Request<?, ParityNextNonceResponse> nonceReq = new Request<>("parity_nextNonce", Collections.singletonList(address), web3jService, ParityNextNonceResponse.class);
			return nonceReq.send().getNextNonce();
		} else {
			return Web3j.build(web3jService).ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).sendAsync().get().getTransactionCount();
		}
	}
}