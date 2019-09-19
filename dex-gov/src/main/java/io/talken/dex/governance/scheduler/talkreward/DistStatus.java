package io.talken.dex.governance.scheduler.talkreward;

import io.talken.common.util.collection.SingleKeyObject;
import lombok.Data;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class DistStatus implements SingleKeyObject<String> {
	private String assetCode;
	private String distributorAddress;
	private AtomicInteger count = new AtomicInteger();
	private BigDecimal amount = BigDecimal.ZERO;

	public DistStatus(String assetCode) {
		this.assetCode = assetCode;
	}

	@Override
	public String __getSKey__() {
		return assetCode;
	}
}
