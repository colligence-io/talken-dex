package io.talken.dex.api.controller.dto;


import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.DexTxmon;

import java.time.LocalDateTime;

/**
 * The type Task transaction result.
 */
public class TaskTransactionResult {
	private DexTxmon dexTxmon;

    /**
     * Instantiates a new Task transaction result.
     *
     * @param dexTxmon the dex txmon
     */
    public TaskTransactionResult(DexTxmon dexTxmon) {
		this.dexTxmon = dexTxmon;
	}

    /**
     * Gets tx id.
     *
     * @return the tx id
     */
    public String getTxId() {return dexTxmon.getTxid();}

    /**
     * Gets task id.
     *
     * @return the task id
     */
    public String getTaskId() {return dexTxmon.getMemotaskid();}

    /**
     * Gets task type.
     *
     * @return the task type
     */
    public DexTaskTypeEnum getTaskType() {return dexTxmon.getTasktype();}

    /**
     * Gets tx hash.
     *
     * @return the tx hash
     */
    public String getTxHash() {return dexTxmon.getTxhash();}

    /**
     * Gets ledger.
     *
     * @return the ledger
     */
    public Long getLedger() {return dexTxmon.getLedger();}

    /**
     * Gets created at.
     *
     * @return the created at
     */
    public LocalDateTime getCreatedAt() {return dexTxmon.getCreatedat();}

    /**
     * Gets source account.
     *
     * @return the source account
     */
    public String getSourceAccount() {return dexTxmon.getSourceaccount();}

    /**
     * Gets envelope xdr.
     *
     * @return the envelope xdr
     */
    public String getEnvelopeXdr() {return dexTxmon.getEnvelopexdr();}

    /**
     * Gets result xdr.
     *
     * @return the result xdr
     */
    public String getResultXdr() {return dexTxmon.getResultxdr();}

    /**
     * Gets result meta xdr.
     *
     * @return the result meta xdr
     */
    public String getResultMetaXdr() {return dexTxmon.getResultmetaxdr();}

    /**
     * Gets fee paid.
     *
     * @return the fee paid
     */
    public Long getFeePaid() {return dexTxmon.getFeepaid();}

    /**
     * Gets process success flag.
     *
     * @return the process success flag
     */
    public Boolean getProcessSuccessFlag() {return dexTxmon.getProcessSuccessFlag();}
}
