package io.colligence.talken.dex.scheduler.refund;

import io.colligence.talken.common.persistence.jooq.tables.pojos.DexCreateofferRefundTask;

import java.time.LocalDateTime;

public class RefundTask extends DexCreateofferRefundTask {
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
