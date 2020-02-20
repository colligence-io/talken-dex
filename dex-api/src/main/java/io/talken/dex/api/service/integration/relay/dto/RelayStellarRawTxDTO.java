package io.talken.dex.api.service.integration.relay.dto;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import lombok.Data;
import org.stellar.sdk.Transaction;

@Deprecated
@Data
public class RelayStellarRawTxDTO {
	private long sequence;
	private String hash;
	private String envelopeXdr;

	public RelayStellarRawTxDTO(Transaction tx) {
		this.sequence = tx.getSequenceNumber();
		this.hash = ByteArrayUtil.toHexString(tx.hash());
		this.envelopeXdr = tx.toEnvelopeXdrBase64();
	}
}
