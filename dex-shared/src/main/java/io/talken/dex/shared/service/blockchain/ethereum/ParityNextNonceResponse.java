package io.talken.dex.shared.service.blockchain.ethereum;

import org.web3j.protocol.core.Response;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

/**
 * parity_nextNonce jsonrpc
 */
public class ParityNextNonceResponse extends Response<String> {
    /**
     * Instantiates a new Parity next nonce response.
     */
    public ParityNextNonceResponse() {
	}

    /**
     * Gets next nonce.
     *
     * @return the next nonce
     */
    public BigInteger getNextNonce() {
		return Numeric.decodeQuantity((String) this.getResult());
	}
}
