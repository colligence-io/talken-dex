package io.talken.dex.governance.scheduler.crawler.cg;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CoinGeckoMarketCapResult<T> {
    private T data;
    private LocalDateTime lastUpdated;
}
