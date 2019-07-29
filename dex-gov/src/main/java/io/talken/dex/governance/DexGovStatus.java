package io.talken.dex.governance;

import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
public class DexGovStatus {
	private TxMonitor txMonitor = new TxMonitor();

	private LocalDateTime lastTradeAggrExecutionTime;

	@Data
	public static class TxMonitor {
		private StellarTxMonitor stellar = new StellarTxMonitor();
		private EthereumTxMonitor ethereum = new EthereumTxMonitor();
		private LuniverseTxMonitor luniverse = new LuniverseTxMonitor();

		@Data
		public static class StellarTxMonitor {
			private String lastPagingToken;
			private LocalDateTime lastTokenTimestamp;
		}

		@Data
		public static class EthereumTxMonitor {
			private BigInteger lastBlock;
			private LocalDateTime lastBlockTimestamp;
		}

		@Data
		public static class LuniverseTxMonitor {
			private BigInteger lastBlock;
			private LocalDateTime lastBlockTimestamp;
		}
	}
}
