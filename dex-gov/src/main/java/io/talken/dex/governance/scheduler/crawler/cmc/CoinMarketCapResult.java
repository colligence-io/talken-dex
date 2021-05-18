package io.talken.dex.governance.scheduler.crawler.cmc;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * The type Coin market cap result.
 *
 * @param <T> the type parameter
 */
@Data
public class CoinMarketCapResult<T> {
	private T data;
	private _Status status;

    /**
     * The type Status.
     */
    @Data
	public static class _Status {
		private LocalDateTime timestamp;
		private int error_code;
		private String error_message;
		private int elapsed;
		private int credit_count;
	}
}
