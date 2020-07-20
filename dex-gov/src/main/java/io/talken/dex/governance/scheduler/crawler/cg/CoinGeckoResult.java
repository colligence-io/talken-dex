package io.talken.dex.governance.scheduler.crawler.cg;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CoinGeckoResult<T> {
    private T data;
    private LocalDateTime lastUpdated;
}
