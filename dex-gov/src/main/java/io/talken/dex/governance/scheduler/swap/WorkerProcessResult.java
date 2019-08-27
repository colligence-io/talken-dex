package io.talken.dex.governance.scheduler.swap;

import com.google.common.base.Throwables;
import io.talken.common.persistence.jooq.tables.pojos.DexTaskSwap;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapLogRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.GSONWriter;
import lombok.Getter;

@Getter
public class WorkerProcessResult {
	private DexTaskSwap taskRecord;
	private String workerName;
	private boolean isSuccess;
	private String errorPosition;
	private String errorCode;
	private String errorMessage;
	private Throwable exception;

	public DexTaskSwapLogRecord newLogRecord() {
		DexTaskSwapLogRecord log = new DexTaskSwapLogRecord();
		log.setSwapId(this.taskRecord.getId());
		log.setTaskSnapshot(GSONWriter.toJsonStringSafe(taskRecord));
		log.setSuccessFlag(isSuccess);
		if(!this.isSuccess) {
			log.setErrorposition(this.workerName + ":" + this.errorPosition);
			log.setErrorcode(this.errorCode);
			log.setErrormessage(this.errorMessage);
			if(exception != null) {
				log.setStacktrace(Throwables.getStackTraceAsString(exception));
			}
		}
		return log;
	}

	public static class Builder {
		private WorkerProcessResult result;

		public Builder(SwapTaskWorker worker, DexTaskSwapRecord taskRecord) {
			this.result = new WorkerProcessResult();
			this.result.workerName = worker.getName();
			this.result.taskRecord = taskRecord.into(DexTaskSwap.class);
		}

		public WorkerProcessResult success() {
			this.result.isSuccess = true;
			return this.result;
		}

		public WorkerProcessResult error(String position, String code, String message) {
			this.result.isSuccess = false;
			this.result.errorPosition = position;
			this.result.errorCode = code;
			this.result.errorMessage = message;
			return this.result;
		}

		public WorkerProcessResult exception(String position, Throwable th) {
			this.result.isSuccess = false;
			this.result.exception = th;
			this.result.errorPosition = position;
			this.result.errorCode = th.getClass().getSimpleName();
			this.result.errorMessage = th.getMessage();
			return this.result;
		}
	}
}
