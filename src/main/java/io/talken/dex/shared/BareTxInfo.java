package io.talken.dex.shared;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import lombok.Data;
import org.stellar.sdk.Network;
import org.stellar.sdk.Transaction;

import java.io.IOException;

@Data
public class BareTxInfo {
	private String networkPhrase;
	private long sequence;
	private String hash;
	private String envelopeXdr;

	public static BareTxInfo build(Transaction tx) throws IOException {
//		// encode tx to envelope,
//	  // bare tx build issue is fixed in stellar sdk
//		TransactionEnvelope xdr = new TransactionEnvelope();
//		xdr.setTx(tx.toXdr());
//		xdr.setSignatures(new DecoratedSignature[0]);
//		ByteArrayOutputStream baos = new ByteArrayOutputStream();
//		XdrDataOutputStream xdos = new XdrDataOutputStream(baos);
//		TransactionEnvelope.encode(xdos, xdr);
//		byte[] txEnvelope = baos.toByteArray();

		// encode to base64
		BareTxInfo txInfo = new BareTxInfo();
		txInfo.setNetworkPhrase(Network.current().getNetworkPassphrase());
		txInfo.setSequence(tx.getSequenceNumber());
		txInfo.setHash(ByteArrayUtil.toHexString(tx.hash()));
		txInfo.setEnvelopeXdr(tx.toEnvelopeXdrBase64());

		return txInfo;
	}
}
