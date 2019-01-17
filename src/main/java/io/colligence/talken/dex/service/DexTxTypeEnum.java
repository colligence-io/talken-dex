package io.colligence.talken.dex.service;

public enum DexTxTypeEnum {
	DEANCHOR(1),
	OFFER(2);

	private final int code;

	DexTxTypeEnum(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}
