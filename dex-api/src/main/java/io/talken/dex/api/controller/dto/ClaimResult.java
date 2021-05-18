package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import lombok.Data;

/**
 * The type Claim result.
 */
@Data
public class ClaimResult {
    private Bctx bctx;
	private Boolean checkStatus;
}
