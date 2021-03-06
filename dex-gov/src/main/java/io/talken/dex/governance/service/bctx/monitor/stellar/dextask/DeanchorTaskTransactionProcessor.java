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
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxReceipt;
import io.talken.dex.shared.service.blockchain.stellar.opreceipt.PaymentOpReceipt;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_DEANCHOR;

/**
 * The type Deanchor task transaction processor.
 */
@Component
public class DeanchorTaskTransactionProcessor implements DexTaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(DeanchorTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

    /**
     * The Tx mgr.
     */
    @Autowired
	protected DataSourceTransactionManager txMgr;

	@Autowired
	private TokenMetaGovService tmService;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.DEANCHOR;
	}

	/**
	 * 1. update dex_task_deanchor signTxCatchFlag
	 * 2. check payment operations is matched with task
	 * 3. queue bctx to transfer holded asset to user
	 *
	 */
	@Override
	public DexTaskTransactionProcessResult process(Long txmId, StellarTxReceipt txResult) {
		try {
			Optional<DexTaskDeanchorRecord> opt_taskRecord = dslContext.selectFrom(DEX_TASK_DEANCHOR).where(DEX_TASK_DEANCHOR.TASKID.eq(txResult.getTaskId().getId())).fetchOptional();

			if(!opt_taskRecord.isPresent())
				throw new DexTaskTransactionProcessError("TaskIdNotFound");

			DexTaskDeanchorRecord taskRecord = opt_taskRecord.get();

			if(taskRecord.getDeanchorBctxId() != null || taskRecord.getSignedTxCatchFlag().equals(true))
				return DexTaskTransactionProcessResult.success();

			// check payments ops matching for deanchortask
			// update task as signed tx catched
			final String from = taskRecord.getTradeaddr(); // from
			final String to = taskRecord.getIssueraddr(); // to
			final BigDecimal amount = taskRecord.getAmount(); // amount

			boolean matchFound = false;

			for(StellarOpReceipt opReceipt : txResult.getOpReceipts()) {
				if(opReceipt instanceof PaymentOpReceipt) {
					PaymentOpReceipt paymentReceipt = (PaymentOpReceipt) opReceipt;

					// found matching payment
					if(paymentReceipt.getRequest().getFrom().equalsIgnoreCase(from) && paymentReceipt.getRequest().getTo().equalsIgnoreCase(to) && paymentReceipt.getRequest().getAmount().compareTo(amount) == 0) {
						logger.info("Transfer to issuer detected : {} -> {} : {} {}({})", from, to, taskRecord.getAmount(), taskRecord.getAssetcode(), taskRecord.getIssueraddr());
						matchFound = true;
					}
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
				logger.error("Matching transfer not found on () for {}", txResult.getTxHash(), txResult.getTaskId());
				taskRecord.setErrorposition("matching_tx");
				taskRecord.setErrorcode("no match found");
				taskRecord.setErrormessage("Matching payment not found in tx");
				taskRecord.update();
			}

			// mark signed tx catch flag to true
			taskRecord.setSignedTxCatchFlag(true);
			taskRecord.update();
		} catch(DexTaskTransactionProcessError error) {
			return DexTaskTransactionProcessResult.error(error);
		} catch(Exception ex) {
			return DexTaskTransactionProcessResult.error("Processing", ex);
		}
		return DexTaskTransactionProcessResult.success();
	}
}
