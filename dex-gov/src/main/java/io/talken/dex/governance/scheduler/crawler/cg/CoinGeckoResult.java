package io.talken.dex.governance.scheduler.crawler.cg;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * The type Coin gecko result.
 *
 * @param <T> the type parameter
 */
@Data
public class CoinGeckoResult<T> {
    private T data;
    private LocalDateTime lastUpdated;
}
