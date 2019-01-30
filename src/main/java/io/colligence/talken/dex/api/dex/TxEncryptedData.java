package io.colligence.talken.dex.api.dex;

public class TxEncryptedData {
	private TxInformation txInfo;
	private String key;
	private String encrypted;

	public TxEncryptedData(TxInformation txInfo) {
		this.txInfo = txInfo;

		encrypt();
	}

	private void encrypt() {
		key = "1";
		encrypted = "2";
	}

	public TxInformation getTxInfo() {
		return txInfo;
	}

	public String getKey() {
		return key;
	}

	public String getEncrypted() {
		return encrypted;
	}
}
