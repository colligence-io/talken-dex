package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * The type Contract list result.
 */
@Data
public class ContractListResult {
    private long total;
    private int totalPage;
    private int pageLimit;

    private List<ContractRequest> rows;
}
