package io.colligence.talken.dex.api.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.colligence.talken.common.persistence.enums.DexTaskTypeEnum;

public class DexTaskId {
	private DexTaskTypeEnum type;
	private String id;
	private long genTime;
	@JsonIgnore
	private String name = "INVALID";

	DexTaskId(DexTaskTypeEnum type, String id, long genTime) {
		this.type = type;
		this.id = id;
		this.genTime = genTime;
		this.name = this.type.name() + " Task " + this.id;
	}

	public DexTaskTypeEnum getType() {
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
}
