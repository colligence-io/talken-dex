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
		return DexSwapStatusEnum.FEECOLLECT_QUEUED;
	}

	@Override
	public void proceed(DexTaskSwapRecord record) {

	}
}
