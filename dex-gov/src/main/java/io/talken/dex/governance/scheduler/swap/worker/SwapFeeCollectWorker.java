package io.talken.dex.governance.scheduler.swap.worker;

import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.dex.governance.scheduler.swap.SwapTaskWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("singleton")
@RequiredArgsConstructor
public class SwapFeeCollectWorker extends SwapTaskWorker {
	@Override
	public DexSwapStatusEnum getStartStatus() {
		// TODO : should be DEANCHOR_ANCSVR_CONFIRMED
		return DexSwapStatusEnum.DEANCHOR_TX_CATCH;
	}

	@Override
	public boolean proceed(DexTaskSwapRecord record) {
		return true;
	}
}
