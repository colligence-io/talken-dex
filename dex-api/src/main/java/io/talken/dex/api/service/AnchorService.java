package io.talken.dex.api.service;


import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.persistence.DexTaskRecord;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.DexTaskAnchorRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeanchorRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
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

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class AnchorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorService.class);

	// constructor injections
	private final StellarNetworkService stellarNetworkService;
	private final TokenMetaService tmService;
	private final FeeCalculationService feeCalculationService;
	private final TradeWalletService twService;
	private final PrivateWalletService pwService;
	private final DSLContext dslContext;

	public PrivateWalletTransferDTO anchor(User user, AnchorRequest request) throws TokenMetaNotFoundException, ActiveAssetHolderAccountNotFoundException, BlockChainPlatformNotSupportedException, TradeWalletRebalanceException, TradeWalletCreateFailedException, SigningException, StellarException, TokenMetaNotManagedException {
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

		String position;

		// create task record
		DexTaskAnchorRecord taskRecord = new DexTaskAnchorRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setBctxType(result.getPlatform());
		taskRecord.setPrivateaddr(request.getPrivateWalletAddress());
		taskRecord.setTradeaddr(tradeWallet.getAccountId());
		taskRecord.setHolderaddr(assetHolderAddress);
		taskRecord.setAssetcode(request.getAssetCode());
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

		logger.info("{} complete. userId = {}", dexTaskId, userId);
		result.setAddrFrom(taskRecord.getPrivateaddr());
		result.setAddrTo(taskRecord.getHolderaddr());
		result.setAddrTrade(taskRecord.getTradeaddr());
		result.setAmount(amount);
		result.setNetfee(taskRecord.getNetworkfee());
		return result;
	}

	public DeanchorResult deanchor(User user, DeanchorRequest request) throws TokenMetaNotFoundException, StellarException, AssetConvertException, EffectiveAmountIsNegativeException, TradeWalletCreateFailedException, SigningException, ActiveAssetHolderAccountNotFoundException, NotEnoughBalanceException, TokenMetaNotManagedException {
		final BigDecimal amount = StellarConverter.scale(request.getAmount());
		final DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.DEANCHOR);
		final KeyPair issuerAccount = tmService.getManagedInfo(request.getAssetCode()).dexIssuerAccount();
		final String assetHolderAddress = tmService.getManagedInfo(request.getAssetCode()).pickActiveHolderAccountAddress();
		final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
		final long userId = user.getId();

		String position;

		// create task record
		DexTaskDeanchorRecord taskRecord = new DexTaskDeanchorRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setTradeaddr(tradeWallet.getAccountId());
		taskRecord.setIssueraddr(issuerAccount.getAccountId());
		taskRecord.setAssetcode(request.getAssetCode());
		taskRecord.setAmount(amount);

		taskRecord.setPrivateaddr(request.getPrivateWalletAddress());
		taskRecord.setHolderaddr(assetHolderAddress);

		taskRecord.setFeebytalk(true);

		CalculateFeeResult feeResult;
		feeResult = feeCalculationService.calculateDeanchorFee(request.getAssetCode(), amount, true);
		BigDecimal deancAmount = StellarConverter.rawToActual(feeResult.getSellAmountRaw());
		BigDecimal feeAmount = StellarConverter.rawToActual(feeResult.getFeeAmountRaw());

		if(!tradeWallet.hasEnough(feeResult.getFeeAssetType(), feeAmount))
			throw new NotEnoughBalanceException(feeResult.getFeeAssetCode(), feeAmount.stripTrailingZeros().toPlainString());
		if(!tradeWallet.hasEnough(feeResult.getSellAssetType(), deancAmount))
			throw new NotEnoughBalanceException(feeResult.getSellAssetCode(), deancAmount.stripTrailingZeros().toPlainString());

		taskRecord.setDeanchoramount(deancAmount);
		taskRecord.setFeeamount(feeAmount);
		taskRecord.setFeecollectoraddr(feeResult.getFeeHolderAccountAddress());

		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);

		position = "build_tx";
		StellarChannelTransaction.Builder sctxBuilder;
		try {
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
									issuerAccount.getAccountId(),
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
		result.setFeeAssetCode("TALK");
		result.setFeeAmount(taskRecord.getFeeamount());
		result.setDeanchorAssetCode(taskRecord.getAssetcode());
		result.setDeanchorAmount(taskRecord.getDeanchoramount());
		return result;
	}
}
