package io.talken.dex.governance.service.bctx.monitor.stellar.dextask;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeanchorRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessError;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessResult;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessor;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarTransferReceipt;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxReceipt;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_DEANCHOR;

@Component
public class DeanchorTaskTransactionProcessor implements DexTaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(DeanchorTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	protected DataSourceTransactionManager txMgr;

	@Autowired
	private TokenMetaGovService tmService;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.DEANCHOR;
	}

	@Override
	public DexTaskTransactionProcessResult process(Long txmId, StellarTxReceipt txResult) {
		try {
			Optional<DexTaskDeanchorRecord> opt_taskRecord = dslContext.selectFrom(DEX_TASK_DEANCHOR).where(DEX_TASK_DEANCHOR.TASKID.eq(txResult.getTaskId().getId())).fetchOptional();

			if(!opt_taskRecord.isPresent())
				throw new DexTaskTransactionProcessError("TaskIdNotFound");

			DexTaskDeanchorRecord taskRecord = opt_taskRecord.get();

			if(taskRecord.getDeanchorBctxId() != null || taskRecord.getSignedTxCatchFlag().equals(true))
				throw new DexTaskTransactionProcessError("DeanchorAlreadyProcessed");

			// mark signed tx catch flag to true
			taskRecord.setSignedTxCatchFlag(true);
			taskRecord.update();

			// check payments ops matching for deanchortask
			// update task as signed tx catched
			final String from = taskRecord.getTradeaddr(); // from
			final String to = taskRecord.getIssueraddr(); // to
			final BigInteger amountRaw = StellarConverter.actualToRaw(taskRecord.getAmount()); // amount

			boolean matchFound = false;
			for(StellarTransferReceipt rcpt : txResult.getPaymentReceipts()) {
				// found matching payment
				if(rcpt.getFrom().equalsIgnoreCase(from) && rcpt.getTo().equalsIgnoreCase(to) && rcpt.getAmountRaw().equals(amountRaw)) {
					matchFound = true;
				}
			}

			if(matchFound) {
				BctxRecord bctxRecord = new BctxRecord();
				TokenMetaTable.Meta meta = tmService.getTokenMeta(taskRecord.getAssetcode());

				bctxRecord.setStatus(BctxStatusEnum.QUEUED);
				bctxRecord.setBctxType(meta.getBctxType());
				bctxRecord.setSymbol(meta.getSymbol());

				switch(meta.getBctxType()) {
					case LUNIVERSE_MAIN_TOKEN:
					case ETHEREUM_ERC20_TOKEN:
						bctxRecord.setPlatformAux(meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString());
						break;
					case STELLAR_TOKEN:
						bctxRecord.setPlatformAux(meta.getAux().get(TokenMetaAuxCodeEnum.STELLAR_ISSUER_ID).toString());
						break;
				}

				bctxRecord.setAddressFrom(taskRecord.getHolderaddr());
				bctxRecord.setAddressTo(taskRecord.getPrivateaddr());
				bctxRecord.setAmount(taskRecord.getDeanchoramount());
				bctxRecord.setNetfee(BigDecimal.ZERO);
				dslContext.attach(bctxRecord);

				TransactionBlockExecutor.of(txMgr).transactional(() -> {
					bctxRecord.store();
					taskRecord.setDeanchorBctxId(bctxRecord.getId());
					logger.info("Deanchor task {} deanchor bctx queued : #{}", taskRecord.getTaskid(), bctxRecord.getId());
					taskRecord.store();
				});
			} else {
				taskRecord.setErrorposition("matching_tx");
				taskRecord.setErrorcode("no match found");
				taskRecord.setErrormessage("Matching payment not found in tx");
				taskRecord.update();
			}
		} catch(DexTaskTransactionProcessError error) {
			return DexTaskTransactionProcessResult.error(error);
		} catch(Exception ex) {
			return DexTaskTransactionProcessResult.error("Processing", ex);
		}
		return DexTaskTransactionProcessResult.success();
	}
}
