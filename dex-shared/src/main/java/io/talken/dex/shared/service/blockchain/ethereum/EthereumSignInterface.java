package io.talken.dex.shared.service.blockchain.ethereum;

import org.web3j.crypto.RawTransaction;

@FunctionalInterface
public interface EthereumSignInterface {
	byte[] sign(RawTransaction tx) throws Exception;
}
