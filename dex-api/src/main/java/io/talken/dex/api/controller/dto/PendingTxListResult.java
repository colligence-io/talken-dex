package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import lombok.Data;

import java.util.List;

@Data
public class PendingTxListResult {
    private boolean status = true;
    private String message = "OK";

    private int recordCount = 0;
    public List<Bctx> bctxs;
}
