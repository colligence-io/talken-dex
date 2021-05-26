package io.talken.dex.governance.scheduler.crawler.netfee;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * The type Stellar network fee result.
 *
 * @param <T> the type parameter
 */
public class StellarNetworkFeeResult<T> {
    private T data;
    private StellarNetworkFeeResult._Status status;

    /**
     * The type Status.
     */
    @Data
    public static class _Status {
        private LocalDateTime timestamp;
    }
}
