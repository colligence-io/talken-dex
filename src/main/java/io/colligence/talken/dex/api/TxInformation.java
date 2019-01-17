package io.colligence.talken.dex.api;

import lombok.Data;

@Data
public class TxInformation {
	private String networkPhrase;
	private long sequence;
	private String hash;
	private String envelopeXdr;
}
