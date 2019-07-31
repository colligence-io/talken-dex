package io.talken.dex.shared.service.blockchain.stellar;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import lombok.Data;
import org.stellar.sdk.Transaction;

import java.io.IOException;

@Data
public class StellarRawTxInfo {
	private long sequence;
	private String hash;
	private String envelopeXdr;

	public static StellarRawTxInfo build(Transaction tx) throws IOException {
		// encode to base64
		StellarRawTxInfo txInfo = new StellarRawTxInfo();
		txInfo.setSequence(tx.getSequenceNumber());
		txInfo.setHash(ByteArrayUtil.toHexString(tx.hash()));
		txInfo.setEnvelopeXdr(tx.toEnvelopeXdrBase64());
		return txInfo;
	}
}
