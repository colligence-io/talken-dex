package io.colligence.talken.dex.service;

import io.colligence.talken.common.persistence.jooq.tables.pojos.DexTxResult;
import io.colligence.talken.common.persistence.jooq.tables.records.DexTxResultRecord;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.dex.misc.dto.TxListRequest;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_TX_RESULT;

@Service
@Scope("singleton")
public class TaskTransactionListService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TaskTransactionListService.class);

	@Autowired
	private DSLContext dslContext;

	public List<TaskTransaction> getTxList(TxListRequest postBody) {
		List<TaskTransaction> rtn = new ArrayList<>();

		Condition condition = DEX_TX_RESULT.SOURCEACCOUNT.eq(postBody.getSourceAccount());

		if(postBody.getOfferId() != null)
			condition = condition.and(DEX_TX_RESULT.OFFERIDFROMRESULT.eq(postBody.getOfferId()));
		if(postBody.getTaskId() != null)
			condition = condition.and(DEX_TX_RESULT.MEMOTASKID.eq(postBody.getTaskId()));
		if(postBody.getTxHash() != null)
			condition = condition.and(DEX_TX_RESULT.TXHASH.eq(postBody.getTxHash()));

		Result<DexTxResultRecord> txList = dslContext.selectFrom(DEX_TX_RESULT)
				.where(condition)
				.orderBy(DEX_TX_RESULT.CREATEDAT.desc())
				.fetch();

		if(txList != null) {
			for(DexTxResultRecord resultRecord : txList) {
				rtn.add(new TaskTransaction(resultRecord.into(DexTxResult.class)));
			}
		}

		return rtn;
	}
}
