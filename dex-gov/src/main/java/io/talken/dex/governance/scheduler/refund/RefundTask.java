package io.talken.dex.governance.scheduler.refund;

import io.talken.common.persistence.jooq.tables.pojos.DexTqueueRefundcreateofferfee;

import java.time.LocalDateTime;

public class RefundTask extends DexTqueueRefundcreateofferfee {
	private Boolean successFlag;
	private Integer trialNo;
	private LocalDateTime logTime;

	public Boolean getSuccessFlag() {
		return successFlag;
	}

	public void setSuccessFlag(Boolean successFlag) {
		this.successFlag = successFlag;
	}

	public Integer getTrialNo() {
		return trialNo;
	}

	public void setTrialNo(Integer trialNo) {
		this.trialNo = trialNo;
	}

	public LocalDateTime getLogTime() {
		return logTime;
	}

	public void setLogTime(LocalDateTime logTime) {
		this.logTime = logTime;
	}
}
