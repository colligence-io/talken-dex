package io.talken.dex.api.service;


import io.talken.common.persistence.jooq.tables.pojos.DexTxmon;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.TaskTransactionResult;
import io.talken.dex.api.controller.dto.TxListRequest;
import org.jooq.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static io.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
public class TaskTransactionListService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TaskTransactionListService.class);

	@Autowired
	private DSLContext dslContext;

	public List<TaskTransactionResult> getTxList(TxListRequest postBody) {
		List<TaskTransactionResult> rtn = new ArrayList<>();

		// base select step
		SelectJoinStep<Record> from = dslContext
				.select(DEX_TXMON.asterisk())
				.from(DEX_TXMON);

		// common condition step
		Condition condition = DEX_TXMON.SOURCEACCOUNT.eq(postBody.getSourceAccount());
//		if(postBody.getOfferId() != null)
//			condition = condition.and(DEX_TXMON.OFFERIDFROMRESULT.eq(postBody.getOfferId()));
		if(postBody.getTaskId() != null)
			condition = condition.and(DEX_TXMON.MEMOTASKID.eq(postBody.getTaskId()));
		if(postBody.getTxHash() != null)
			condition = condition.and(DEX_TXMON.TXHASH.eq(postBody.getTxHash()));
//		TODO : fix this in 1.3.0
//		// join select step if request contains search condition for asset code
//		if((postBody.getBuyAssetCode() != null && !postBody.getBuyAssetCode().isEmpty()) ||
//				(postBody.getSellAssetCode() != null && !postBody.getSellAssetCode().isEmpty())) {
//
//			from = from.leftJoin(DEX_TXMON_CREATEOFFER).on(DEX_TXMON_CREATEOFFER.TXM_ID.eq(DEX_TXMON.ID))
//					.leftJoin(DEX_TASK_CREATEOFFER).on(DEX_TASK_CREATEOFFER.TASKID.eq(DEX_TXMON_CREATEOFFER.TASKID_CROF));
//
//			if(postBody.getBuyAssetCode() != null && !postBody.getBuyAssetCode().isEmpty())
//				condition = condition.and(DEX_TASK_CREATEOFFER.BUYASSETCODE.eq(postBody.getBuyAssetCode()));
//			if(postBody.getSellAssetCode() != null && !postBody.getSellAssetCode().isEmpty())
//				condition = condition.and(DEX_TASK_CREATEOFFER.SELLASSETCODE.eq(postBody.getSellAssetCode()));
//		}

		Result<Record> txList = from.where(condition)
				.orderBy(DEX_TXMON.CREATEDAT.desc())
				.fetch();

		if(txList != null) {
			for(Record resultRecord : txList) {
				rtn.add(new TaskTransactionResult(resultRecord.into(DexTxmon.class)));
			}
		}

		return rtn;
	}
}
