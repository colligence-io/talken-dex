package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class StakingEventListResult {
    private long total;
    private int totalPage;
    private int pageLimit;

    private long totalPrepare;
    private long totalOpen;
    private long totalClosed;

    public List<StakingEventRequest> stakingEventList;
}
