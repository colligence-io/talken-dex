package io.colligence.talken.dex.api;

import lombok.Getter;

import java.util.UUID;

@Getter
public class DexTaskId {
	private Type type;
	private String uuid;
	private String id;

	private DexTaskId(Type type) {
		this.type = type;
		this.uuid = UUID.randomUUID().toString();

		this.id = (type.getCode() + uuid).replaceAll("[^0-9a-zA-Z]", "").toLowerCase();
	}

	public static DexTaskId generate(Type type) {
		return new DexTaskId(type);
	}

	@Override
	public String toString() {
		return this.id;
	}

	public static enum Type {
		ANCHOR(1),
		DEANCHOR(2),
		OFFER_MAKE(3),
		OFFER_DELETE(4);

		private final int code;

		Type(int code) {
			this.code = code;
		}

		public int getCode() {
			return code;
		}
	}
}
