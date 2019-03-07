package io.colligence.talken.dex.api.service;

import io.colligence.talken.common.persistence.jooq.tables.pojos.DexTxResult;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.dto.TaskTransactionResult;
import io.colligence.talken.dex.api.dto.TxListRequest;
import org.jooq.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static io.colligence.talken.common.persistence.jooq.Tables.*;

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
				.select(DEX_TX_RESULT.asterisk())
				.from(DEX_TX_RESULT);

		// common condition step
		Condition condition = DEX_TX_RESULT.SOURCEACCOUNT.eq(postBody.getSourceAccount());
		if(postBody.getOfferId() != null)
			condition = condition.and(DEX_TX_RESULT.OFFERIDFROMRESULT.eq(postBody.getOfferId()));
		if(postBody.getTaskId() != null)
			condition = condition.and(DEX_TX_RESULT.MEMOTASKID.eq(postBody.getTaskId()));
		if(postBody.getTxHash() != null)
			condition = condition.and(DEX_TX_RESULT.TXHASH.eq(postBody.getTxHash()));

		// join select step if request contains search condition for asset code
		if((postBody.getBuyAssetCode() != null && !postBody.getBuyAssetCode().isEmpty()) ||
				(postBody.getSellAssetCode() != null && !postBody.getSellAssetCode().isEmpty())) {

			from = from.leftJoin(DEX_TX_RESULT_CREATEOFFER).on(DEX_TX_RESULT_CREATEOFFER.TXID.eq(DEX_TX_RESULT.TXID))
					.leftJoin(DEX_CREATEOFFER_TASK).on(DEX_CREATEOFFER_TASK.TASKID.eq(DEX_TX_RESULT_CREATEOFFER.TASKID));

			if(postBody.getBuyAssetCode() != null && !postBody.getBuyAssetCode().isEmpty())
				condition = condition.and(DEX_CREATEOFFER_TASK.BUYASSETCODE.eq(postBody.getBuyAssetCode()));
			if(postBody.getSellAssetCode() != null && !postBody.getSellAssetCode().isEmpty())
				condition = condition.and(DEX_CREATEOFFER_TASK.SELLASSETCODE.eq(postBody.getSellAssetCode()));
		}

		Result<Record> txList = from.where(condition)
				.orderBy(DEX_TX_RESULT.CREATEDAT.desc())
				.fetch();

		if(txList != null) {
			for(Record resultRecord : txList) {
				rtn.add(new TaskTransactionResult(resultRecord.into(DexTxResult.class)));
			}
		}

		return rtn;
	}
}
