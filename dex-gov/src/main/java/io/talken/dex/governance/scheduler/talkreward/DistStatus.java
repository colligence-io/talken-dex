package io.talken.dex.governance.scheduler.talkreward;

import io.talken.common.util.collection.SingleKeyObject;
import lombok.Data;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The type Dist status.
 */
@Data
public class DistStatus implements SingleKeyObject<String> {
	private String assetCode;
	private AtomicInteger count = new AtomicInteger();
	private BigDecimal amount = BigDecimal.ZERO;

    /**
     * Instantiates a new Dist status.
     *
     * @param assetCode the asset code
     */
    public DistStatus(String assetCode) {
		this.assetCode = assetCode;
	}

	@Override
	public String __getSKey__() {
		return assetCode;
	}
}
