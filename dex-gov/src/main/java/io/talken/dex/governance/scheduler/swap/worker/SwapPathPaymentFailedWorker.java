package io.talken.dex.governance.scheduler.swap.worker;

import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.dex.governance.scheduler.swap.SwapTaskWorker;
import io.talken.dex.governance.scheduler.swap.WorkerProcessResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Service Disabled [TKN-1426]
//@Component
@Scope("singleton")
@RequiredArgsConstructor
public class SwapPathPaymentFailedWorker extends SwapTaskWorker {
	@Override
	protected int getRetryCount() {
		return 0;
	}

	@Override
	protected Duration getRetryInterval() {
		return null;
	}

	@Override
	public DexSwapStatusEnum getStartStatus() {
		return DexSwapStatusEnum.PATHPAYMENT_FAILED;
	}

	@Override
	public WorkerProcessResult proceed(DexTaskSwapRecord record) {
		record.setStatus(DexSwapStatusEnum.REFUND_DEANCHOR_QUEUED);
		record.update();
		return new WorkerProcessResult.Builder(this, record).success();
	}
}
