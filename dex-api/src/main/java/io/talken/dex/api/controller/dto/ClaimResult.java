package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import lombok.Data;

@Data
public class ClaimResult {
    private Bctx bctx;
	private Boolean checkStatus;
}
