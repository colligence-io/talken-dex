package io.talken.dex.governance.scheduler.crawler.netfee;

import lombok.Data;

import java.time.LocalDateTime;

public class StellarNetworkFeeResult<T> {
    private T data;
    private StellarNetworkFeeResult._Status status;

    @Data
    public static class _Status {
        private LocalDateTime timestamp;
    }
}
