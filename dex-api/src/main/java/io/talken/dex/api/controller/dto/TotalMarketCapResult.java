package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TotalMarketCapResult {
    private String totalMarketCap;
    private String totalVolume;
    private String marketCapPer;
    private String marketCapChangePercentage24hUsd;
}
