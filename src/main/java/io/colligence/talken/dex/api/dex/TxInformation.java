package io.colligence.talken.dex.api.dex;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import lombok.Data;
import org.stellar.sdk.Network;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.xdr.DecoratedSignature;
import org.stellar.sdk.xdr.TransactionEnvelope;
import org.stellar.sdk.xdr.XdrDataOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Data
public class TxInformation {
	private String networkPhrase;
	private long sequence;
	private String hash;
	private String envelopeXdr;

	public static TxInformation buildTxInformation(Transaction tx) throws IOException {
		// encode tx to envelope
		TransactionEnvelope xdr = new TransactionEnvelope();
		xdr.setTx(tx.toXdr());
		xdr.setSignatures(new DecoratedSignature[0]);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		XdrDataOutputStream xdos = new XdrDataOutputStream(baos);
		TransactionEnvelope.encode(xdos, xdr);
		byte[] txEnvelope = baos.toByteArray();

		// encode to base64
		TxInformation txInfo = new TxInformation();
		txInfo.setNetworkPhrase(Network.current().getNetworkPassphrase());
		txInfo.setSequence(tx.getSequenceNumber());
		txInfo.setHash(ByteArrayUtil.toHexString(tx.hash()));
		txInfo.setEnvelopeXdr(Base64.getEncoder().encodeToString(txEnvelope));

		return txInfo;
	}
}
