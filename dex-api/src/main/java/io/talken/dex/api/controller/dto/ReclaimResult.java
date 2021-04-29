package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
import lombok.Data;

@Data
public class ReclaimResult {
    private Bctx bctx;
	private BctxLog bctxLog;
}
