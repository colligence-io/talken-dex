package io.talken.dex.api.dto;


import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.DexTxResult;

import java.time.LocalDateTime;

public class TaskTransactionResult {
	private DexTxResult dexTxResult;

	public TaskTransactionResult(DexTxResult dexTxResult) {
		this.dexTxResult = dexTxResult;
	}

	public String getTxId() {return dexTxResult.getTxid();}

	public String getTaskId() {return dexTxResult.getMemotaskid();}

	public DexTaskTypeEnum getTaskType() {return dexTxResult.getTasktype();}

	public String getTxHash() {return dexTxResult.getTxhash();}

	public Long getLedger() {return dexTxResult.getLedger();}

	public LocalDateTime getCreatedAt() {return dexTxResult.getCreatedat();}

	public String getSourceAccount() {return dexTxResult.getSourceaccount();}

	public String getEnvelopeXdr() {return dexTxResult.getEnvelopexdr();}

	public String getResultXdr() {return dexTxResult.getResultxdr();}

	public String getResultMetaXdr() {return dexTxResult.getResultmetaxdr();}

	public Long getFeePaid() {return dexTxResult.getFeepaid();}

	public Long getOfferId() {return dexTxResult.getOfferidfromresult();}

	public Boolean getProcessSuccessFlag() {return dexTxResult.getProcessSuccessFlag();}
}
