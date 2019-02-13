package io.colligence.talken.dex.api.service;

public enum ManagedAccountTypeEnum {
	ASSET_HOLDER(0),
	ASSET_ISSUER(1),
	ASSET_BASE(2),
	FEECOLLECTOR_OFFER(3),
	FEECOLLECTOR_DEANCHOR(4);

	private final int code;

	ManagedAccountTypeEnum(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}
