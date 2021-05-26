package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The type Total market cap result.
 */
@Data
public class TotalMarketCapResult {
    private long active_cryptocurrencies;
    private long upcoming_icos;
    private long ongoing_icos;
    private long ended_icos;
    private long markets;
    private Map total_market_cap;
    private Map total_volume;
    private Map market_cap_percentage;
    private BigDecimal market_cap_change_percentage_24h_usd;
    private long updated_at;
    private String last_updated;
}
