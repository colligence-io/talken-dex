package io.talken.dex.api.service;


import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.DexTaskRecord;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.DexTaskAnchorRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeanchorRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.dex.api.controller.dto.AnchorRequest;
import io.talken.dex.api.controller.dto.CalculateFeeResult;
import io.talken.dex.api.controller.dto.DeanchorRequest;
import io.talken.dex.api.controller.dto.DeanchorResult;
import io.talken.dex.api.service.integration.PrivateWalletMsgTypeEnum;
import io.talken.dex.api.service.integration.PrivateWalletService;
import io.talken.dex.api.service.integration.PrivateWalletTransferDTO;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.*;
import io.talken.dex.shared.service.blockchain.stellar.StellarChannelTransaction;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.blockchain.stellar.StellarSignerAccount;
import io.talken.dex.shared.service.integration.anchor.*;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class AnchorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorService.class);

	// constructor injections
	private final StellarNetworkService stellarNetworkService;
	private final AnchorServerService anchorServerService;
	private final TokenMetaService tmService;
	private final FeeCalculationService feeCalculationService;
	private final TradeWalletService twService;
	private final PrivateWalletService pwService;
	private final DSLContext dslContext;

	public PrivateWalletTransferDTO anchor(User user, AnchorRequest request) throws TokenMetaNotFoundException, IntegrationException, ActiveAssetHolderAccountNotFoundException, BlockChainPlatformNotSupportedException, TradeWalletRebalanceException, TradeWalletCreateFailedException, SigningException, StellarException {
		final BigDecimal amount = StellarConverter.scale(request.getAmount());
		final DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.ANCHOR);
		final String assetHolderAddress = tmService.getActiveHolderAccountAddress(request.getAssetCode());
		final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
		final long userId = user.getId();

		String position;

		// create task record
		DexTaskAnchorRecord taskRecord = new DexTaskAnchorRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setPrivateaddr(request.getPrivateWalletAddress());
		taskRecord.setTradeaddr(tradeWallet.getAccountId());
		taskRecord.setHolderaddr(assetHolderAddress);
		taskRecord.setAssetcode(request.getAssetCode());
		taskRecord.setAmountraw(StellarConverter.actualToRaw(amount).longValueExact());
		taskRecord.setNetworkfee(request.getNetworkFee());
		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);

		// Adjust native balance before anchor
		position = "rebalance";
		StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();
		try {
			ObjectPair<Boolean, BigDecimal> rebalanced = twService.addNativeBalancingOperation(sctxBuilder, tradeWallet, false, request.getAssetCode());
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
				} catch(IOException e) {
					throw new StellarException(e);
				}
			}
		} catch(TalkenException tex) {
			DexTaskRecord.writeError(taskRecord, position, tex);
			throw tex;
		}

		position = "req_anc";
		try {
			AncServerAnchorRequest anchor_request = new AncServerAnchorRequest();
			anchor_request.setTaskId(dexTaskId.getId());
			anchor_request.setUid(String.valueOf(userId));
			anchor_request.setFrom(request.getPrivateWalletAddress());
			anchor_request.setTo(assetHolderAddress);
			anchor_request.setStellar(tradeWallet.getAccountId());
			anchor_request.setSymbol(request.getAssetCode());
			anchor_request.setValue(amount);
			anchor_request.setMemo(UTCUtil.getNow().toString());

			// request anchor monitor
			IntegrationResult<AncServerAnchorResponse> anchorResult = anchorServerService.requestAnchor(anchor_request);
			if(!anchorResult.isSuccess()) {
				throw new IntegrationException(anchorResult);
			}

			taskRecord.setAncIndex(anchorResult.getData().getData().getIndex());
			taskRecord.store();
		} catch(TalkenException tex) {
			DexTaskRecord.writeError(taskRecord, position, tex);
			throw tex;
		}

		logger.info("{} complete. userId = {}", dexTaskId, userId);
		PrivateWalletTransferDTO result = pwService.createTransferDTObase(PrivateWalletMsgTypeEnum.ANCHOR, taskRecord.getAssetcode());
		result.setAddrFrom(taskRecord.getPrivateaddr());
		result.setAddrTo(taskRecord.getHolderaddr());
		result.setAddrTrade(taskRecord.getTradeaddr());
		result.setAmount(StellarConverter.rawToActual(taskRecord.getAmountraw()));
		result.setNetfee(taskRecord.getNetworkfee());
		return result;
	}

	public DeanchorResult deanchor(User user, DeanchorRequest request) throws TokenMetaNotFoundException, StellarException, AssetConvertException, EffectiveAmountIsNegativeException, IntegrationException, TradeWalletCreateFailedException, SigningException {
		final BigDecimal amount = StellarConverter.scale(request.getAmount());
		final boolean feeByTalk = Optional.ofNullable(request.getFeeByTalk()).orElse(false);
		final DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.DEANCHOR);
		final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
		final long userId = user.getId();

		String position;

		// create task record
		DexTaskDeanchorRecord taskRecord = new DexTaskDeanchorRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setPrivateaddr(request.getPrivateWalletAddress());
		taskRecord.setTradeaddr(tradeWallet.getAccountId());
		taskRecord.setAssetcode(request.getAssetCode());
		taskRecord.setAmountraw(StellarConverter.actualToRaw(amount).longValueExact());
		taskRecord.setNetworkfee(StellarConverter.rawToActual(stellarNetworkService.getNetworkFee()));
		taskRecord.setFeebyctx(feeByTalk);
		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);

		position = "calc_fee";
		CalculateFeeResult feeResult;
		try {
			// calculate fee
			feeResult = feeCalculationService.calculateDeanchorFee(request.getAssetCode(), amount, feeByTalk);
			// set amount log
			taskRecord.setDeanchoramountraw(feeResult.getSellAmountRaw().longValueExact());
			taskRecord.setFeeamountraw(feeResult.getFeeAmountRaw().longValueExact());
			taskRecord.setFeeassettype(StellarConverter.toAssetCode(feeResult.getFeeAssetType()));
			taskRecord.setFeecollectaccount(feeResult.getFeeHolderAccountAddress());
		} catch(TalkenException tex) {
			DexTaskRecord.writeError(taskRecord, position, tex);
			throw tex;
		}

		position = "build_tx";
		StellarChannelTransaction.Builder sctxBuilder;
		try {
			// get base account
			KeyPair baseAccount = tmService.getBaseAccount(request.getAssetCode());
			taskRecord.setBaseaccount(baseAccount.getAccountId());

			sctxBuilder = stellarNetworkService.newChannelTxBuilder();

			// build fee operation
			if(feeResult.getFeeAmountRaw().compareTo(BigInteger.ZERO) > 0) {
				sctxBuilder
						.addOperation(
								new PaymentOperation
										.Builder(
										feeResult.getFeeHolderAccountAddress(),
										feeResult.getFeeAssetType(),
										StellarConverter.rawToActualString(feeResult.getFeeAmountRaw())
								).setSourceAccount(tradeWallet.getAccountId())
										.build()
						)
						.addSigner(new StellarSignerAccount(twService.extractKeyPair(tradeWallet)));
			}

			// build deanchor operation
			sctxBuilder
					.setMemo(dexTaskId.getId())
					.addOperation(
							new PaymentOperation
									.Builder(
									baseAccount.getAccountId(),
									feeResult.getSellAssetType(),
									StellarConverter.rawToActualString(feeResult.getSellAmountRaw())
							).setSourceAccount(tradeWallet.getAccountId())
									.build()
					)
					.addSigner(new StellarSignerAccount(twService.extractKeyPair(tradeWallet)));
		} catch(TalkenException tex) {
			DexTaskRecord.writeError(taskRecord, position, tex);
			throw tex;
		}

		position = "build_sctx";
		// put tx info and submit tx
		try(StellarChannelTransaction sctx = sctxBuilder.build()) {
			position = "req_anc";
			// request deanchor monitor
			AncServerDeanchorRequest deanchor_request = new AncServerDeanchorRequest();
			deanchor_request.setTaskId(dexTaskId.getId());
			deanchor_request.setUid(String.valueOf(userId));
			deanchor_request.setSymbol(taskRecord.getAssetcode());
			deanchor_request.setHash(taskRecord.getTxHash());
			deanchor_request.setFrom(taskRecord.getTradeaddr());
			deanchor_request.setTo(taskRecord.getBaseaccount());
			deanchor_request.setAddress(taskRecord.getPrivateaddr());
			deanchor_request.setValue(StellarConverter.rawToActual(taskRecord.getDeanchoramountraw()).doubleValue());
			deanchor_request.setMemo(UTCUtil.getNow().toString());

			IntegrationResult<AncServerDeanchorResponse> deanchorResult = anchorServerService.requestDeanchor(deanchor_request);

			if(!deanchorResult.isSuccess()) {
				throw new IntegrationException(deanchorResult);
			}

			// insert task record
			taskRecord.setAncIndex(deanchorResult.getData().getData().getIndex());

			taskRecord.setTxSeq(sctx.getTx().getSequenceNumber());
			taskRecord.setTxHash(ByteArrayUtil.toHexString(sctx.getTx().hash()));
			taskRecord.setTxXdr(sctx.getTx().toEnvelopeXdrBase64());
			taskRecord.store();

			position = "submit_sctx";
			SubmitTransactionResponse txResponse = sctx.submit();

			if(!txResponse.isSuccess()) {
				throw StellarException.from(txResponse);
			}
		} catch(TalkenException tex) {
			DexTaskRecord.writeError(taskRecord, position, tex);
			throw tex;
		} catch(IOException ioex) {
			StellarException ex = new StellarException(ioex);
			DexTaskRecord.writeError(taskRecord, position, ex);
			throw ex;
		}

		logger.info("{} complete. userId = {}", dexTaskId, userId);
		DeanchorResult result = new DeanchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setTxHash(taskRecord.getTxHash());
		result.setFeeAssetCode(taskRecord.getFeeassettype());
		result.setFeeAmount(StellarConverter.rawToActual(taskRecord.getFeeamountraw()));
		result.setDeanchorAssetCode(taskRecord.getAssetcode());
		result.setDeanchorAmount(StellarConverter.rawToActual(taskRecord.getDeanchoramountraw()));
		return result;
	}
}
