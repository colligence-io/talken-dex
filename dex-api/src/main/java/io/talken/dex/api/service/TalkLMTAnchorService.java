package io.talken.dex.api.service;


import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.persistence.DexTaskRecord;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.DexTaskAnchorRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.api.controller.dto.AnchorRequest;
import io.talken.dex.api.service.integration.PrivateWalletMsgTypeEnum;
import io.talken.dex.api.service.integration.PrivateWalletService;
import io.talken.dex.api.service.integration.PrivateWalletTransferDTO;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.*;
import io.talken.dex.shared.service.blockchain.stellar.StellarChannelTransaction;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.DatePart;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.AccountRequiresMemoException;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_ANCHOR;

@Deprecated
@Service
@Scope("singleton")
@RequiredArgsConstructor
public class TalkLMTAnchorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TalkLMTAnchorService.class);

	// constructor injections
	private final StellarNetworkService stellarNetworkService;
	private final TokenMetaApiService tmService;
	private final TradeWalletService twService;
	private final PrivateWalletService pwService;
	private final DSLContext dslContext;

	private final static String TALK = "TALK";

	/**
	 * Create Anchor task
	 * 1. rebalance user tradewallet for trustline
	 * 2. create trustline for requested asset (and USDT)
	 * 3. insert task to dex_task_anchor
	 * 4. return transferDTObase to client
	 * 5. after client(and wallet server) make tx dex-gov will catch tx and issue asset to user trade wallet
	 *
	 * @param user
	 * @param request
	 * @return
	 * @throws TokenMetaNotFoundException
	 * @throws ActiveAssetHolderAccountNotFoundException
	 * @throws BlockChainPlatformNotSupportedException
	 * @throws TradeWalletRebalanceException
	 * @throws TradeWalletCreateFailedException
	 * @throws SigningException
	 * @throws StellarException
	 * @throws TokenMetaNotManagedException
	 * @throws DuplicatedTaskFoundException
	 */
	public PrivateWalletTransferDTO anchor(User user, AnchorRequest request) throws TokenMetaNotFoundException, ActiveAssetHolderAccountNotFoundException, BlockChainPlatformNotSupportedException, TradeWalletRebalanceException, TradeWalletCreateFailedException, SigningException, StellarException, TokenMetaNotManagedException, DuplicatedTaskFoundException {
		final BigDecimal amount = StellarConverter.scale(request.getAmount());
		final DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.ANCHOR);
		final String assetHolderAddress = tmService.getManagedInfo(request.getAssetCode()).pickActiveHolderAccountAddress();
		final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
		final long userId = user.getId();

		PrivateWalletTransferDTO result = pwService.createTransferDTObase(PrivateWalletMsgTypeEnum.ANCHOR, request.getAssetCode());

		String platform_aux = null;
		if(result.getPlatform().getAuxCode() != null) {
			if(!result.getAux().containsKey(result.getPlatform().getAuxCode().name()))
				throw new BlockChainPlatformNotSupportedException("No aux data for " + request.getAssetCode() + " found on meta.");
			platform_aux = result.getAux().get(result.getPlatform().getAuxCode().name()).toString();
		}

		// check there is same unchecked request within 1 minutes
		Optional<DexTaskAnchorRecord> sameRequest = dslContext.selectFrom(DEX_TASK_ANCHOR).where(
				DEX_TASK_ANCHOR.PRIVATEADDR.eq(request.getPrivateWalletAddress())
						.and(DEX_TASK_ANCHOR.BCTX_TYPE.eq(BlockChainPlatformEnum.LUNIVERSE_MAIN_TOKEN))
                        .and(DEX_TASK_ANCHOR.ASSETCODE.eq(TALK))
						.and(DEX_TASK_ANCHOR.AMOUNT.eq(amount))
						.and(DEX_TASK_ANCHOR.BC_REF_ID.isNull())
						.and(DEX_TASK_ANCHOR.CREATE_TIMESTAMP.gt(DSL.localDateTimeSub(DSL.currentLocalDateTime(), 60, DatePart.SECOND)))
		).limit(1).fetchOptional();

		if(sameRequest.isPresent()) {
			throw new DuplicatedTaskFoundException(sameRequest.get().getTaskid());
		}

		String position;

		// create task record
		DexTaskAnchorRecord taskRecord = new DexTaskAnchorRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setBctxType(result.getPlatform());
		taskRecord.setPrivateaddr(request.getPrivateWalletAddress());
		taskRecord.setTradeaddr(tradeWallet.getAccountId());
		taskRecord.setHolderaddr(assetHolderAddress);
		taskRecord.setAssetcode(TALK);
		taskRecord.setPlatformAux(platform_aux);
		taskRecord.setAmount(amount);
		taskRecord.setNetworkfee(request.getNetworkFee());
		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);

		// Adjust native balance before anchor
		position = "rebalance";
		StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();
		try {
			ObjectPair<Boolean, BigDecimal> rebalanced = twService.addNativeBalancingOperation(sctxBuilder, tradeWallet, false, TALK);
			if(rebalanced.first()) {
				try {
					logger.debug("Rebalance trade wallet {} (#{}) for anchor task.", tradeWallet.getAccountId(), userId);
					SubmitTransactionResponse rebalanceResponse = sctxBuilder.buildAndSubmit();
					if(!rebalanceResponse.isSuccess()) {
						ObjectPair<String, String> errorInfo = StellarConverter.getResultCodesFromExtra(rebalanceResponse);
						logger.error("Cannot rebalance trade wallet {} {} : {} {}", user.getId(), tradeWallet.getAccountId(), errorInfo.first(), errorInfo.second());
						throw new TradeWalletRebalanceException(errorInfo.first());
					}

					taskRecord.setRebalanceamount(rebalanced.second());
					taskRecord.setRebalancetxhash(rebalanceResponse.getHash());
					taskRecord.store();
				} catch(IOException | AccountRequiresMemoException e) {
					throw new StellarException(e);
				}
			}
		} catch(TalkenException tex) {
			DexTaskRecord.writeError(taskRecord, position, tex);
			throw tex;
		}

		logger.info("{} complete. userId = {}", dexTaskId, userId);
		result.setAddrFrom(taskRecord.getPrivateaddr());
		result.setAddrTo(taskRecord.getHolderaddr());
		result.setAddrTrade(taskRecord.getTradeaddr());
		result.setAmount(amount);
		result.setNetfee(taskRecord.getNetworkfee());
		result.setSymbol(TALK);

		return result;
	}
}
