package io.colligence.talken.dex.api.dex;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DexTaskId {
	private Type type;
	private String id;
	private long genTime;
	@JsonIgnore
	private String name = "INVALID";

	DexTaskId(Type type, String id, long genTime) {
		this.type = type;
		this.id = id;
		this.genTime = genTime;
		this.name = this.type.name() + " Task " + this.id;
	}

	public Type getType() {
		return type;
	}

	public String getId() {
		return id;
	}

	public long getGenTime() {
		return genTime;
	}

	@Override
	public String toString() {
		return this.name;
	}

	public static enum Type {
		ANCHOR(1),
		DEANCHOR(2),
		OFFER_CREATE(3),
		OFFER_CREATEPASSIVE(4),
		OFFER_DELETE(5),
		OFFER_REFUNDFEE(6);

		private final int code;

		Type(int code) {
			this.code = code;
		}

		public int getCode() {
			return code;
		}

		public static Type fromCode(char c) {
			switch(c) {
				case '1':
					return Type.ANCHOR;
				case '2':
					return Type.DEANCHOR;
				case '3':
					return Type.OFFER_CREATE;
				case '4':
					return Type.OFFER_CREATEPASSIVE;
				case '5':
					return Type.OFFER_DELETE;
				default:
					return null;
			}
		}

	}
}
