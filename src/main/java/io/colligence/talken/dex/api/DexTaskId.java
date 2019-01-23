package io.colligence.talken.dex.api;

import io.colligence.talken.dex.exception.TaskIntegrityCheckFailedException;
import lombok.Getter;

import java.util.UUID;

@Getter
public class DexTaskId {
	private Type type;
	private String id;

	private static final int LENGTH = 48;

	private DexTaskId(Type type) {
		this.type = type;
		String uuid = UUID.randomUUID().toString();
		String uuid2 = UUID.randomUUID().toString();

		this.id = (type.getCode() + uuid + uuid2).replaceAll("[^0-9a-zA-Z]", "").toLowerCase().substring(0, LENGTH);
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
		OFFER_CREATE(3),
		OFFER_CREATEPASSIVE(4),
		OFFER_DELETE(5);

		private final int code;

		Type(int code) {
			this.code = code;
		}

		public int getCode() {
			return code;
		}
	}

	public static Type getType(String taskId) throws TaskIntegrityCheckFailedException {
		if(taskId.length() != LENGTH) throw new TaskIntegrityCheckFailedException(taskId);

		switch(Integer.valueOf(taskId.substring(0, 1))) {
			case 1:
				return Type.ANCHOR;
			case 2:
				return Type.DEANCHOR;
			case 3:
				return Type.OFFER_CREATE;
			case 4:
				return Type.OFFER_CREATEPASSIVE;
			case 5:
				return Type.OFFER_DELETE;
			default:
				throw new TaskIntegrityCheckFailedException(taskId);
		}
	}
}
