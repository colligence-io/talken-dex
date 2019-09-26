package io.talken.dex.shared.service.blockchain.ethereum;

import org.web3j.protocol.core.Response;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

public class ParityNextNonceResponse extends Response<String> {
	public ParityNextNonceResponse() {
	}

	public BigInteger getNextNonce() {
		return Numeric.decodeQuantity((String) this.getResult());
	}
}
