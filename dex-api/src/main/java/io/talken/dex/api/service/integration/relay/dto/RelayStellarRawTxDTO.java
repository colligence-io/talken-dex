package io.talken.dex.api.service.integration.relay.dto;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import lombok.Data;
import org.stellar.sdk.Transaction;

/**
 * The type Relay stellar raw tx dto.
 */
@Deprecated
@Data
public class RelayStellarRawTxDTO {
	private long sequence;
	private String hash;
	private String envelopeXdr;

    /**
     * Instantiates a new Relay stellar raw tx dto.
     *
     * @param tx the tx
     */
    public RelayStellarRawTxDTO(Transaction tx) {
		this.sequence = tx.getSequenceNumber();
		this.hash = ByteArrayUtil.toHexString(tx.hash());
		this.envelopeXdr = tx.toEnvelopeXdrBase64();
	}
}
