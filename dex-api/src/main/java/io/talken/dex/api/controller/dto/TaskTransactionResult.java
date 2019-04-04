package io.talken.dex.api.controller.dto;


import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.DexTxmon;

import java.time.LocalDateTime;

public class TaskTransactionResult {
	private DexTxmon dexTxmon;

	public TaskTransactionResult(DexTxmon dexTxmon) {
		this.dexTxmon = dexTxmon;
	}

	public String getTxId() {return dexTxmon.getTxid();}

	public String getTaskId() {return dexTxmon.getMemotaskid();}

	public DexTaskTypeEnum getTaskType() {return dexTxmon.getTasktype();}

	public String getTxHash() {return dexTxmon.getTxhash();}

	public Long getLedger() {return dexTxmon.getLedger();}

	public LocalDateTime getCreatedAt() {return dexTxmon.getCreatedat();}

	public String getSourceAccount() {return dexTxmon.getSourceaccount();}

	public String getEnvelopeXdr() {return dexTxmon.getEnvelopexdr();}

	public String getResultXdr() {return dexTxmon.getResultxdr();}

	public String getResultMetaXdr() {return dexTxmon.getResultmetaxdr();}

	public Long getFeePaid() {return dexTxmon.getFeepaid();}

	public Long getOfferId() {return dexTxmon.getOfferidfromresult();}

	public Boolean getProcessSuccessFlag() {return dexTxmon.getProcessSuccessFlag();}
}
