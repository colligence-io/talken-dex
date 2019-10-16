package io.talken.dex.api.service;


import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.DexTaskRecord;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.DexTaskCreateofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeleteofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTxmonCreateofferRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.api.controller.dto.*;
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
import org.stellar.sdk.Asset;
import org.stellar.sdk.ManageBuyOfferOperation;
import org.stellar.sdk.ManageSellOfferOperation;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TXMON_CREATEOFFER;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class OfferService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(OfferService.class);

	// constructor injections
	private final FeeCalculationService feeCalculationService;
	private final StellarNetworkService stellarNetworkService;
	private final TradeWalletService twService;
	private final TokenMetaService tmService;
	private final DSLContext dslContext;

	public CreateOfferResult createSellOffer(User user, CreateOfferRequest request) throws TokenMetaNotFoundException, SigningException, StellarException, AssetConvertException, EffectiveAmountIsNegativeException, TradeWalletCreateFailedException, TradeWalletRebalanceException {
		return createOffer(user, DexTaskTypeEnum.OFFER_CREATE_SELL, request);
	}

	public CreateOfferResult createBuyOffer(User user, CreateOfferRequest request) throws TokenMetaNotFoundException, SigningException, StellarException, AssetConvertException, EffectiveAmountIsNegativeException, TradeWalletCreateFailedException, TradeWalletRebalanceException {
		return createOffer(user, DexTaskTypeEnum.OFFER_CREATE_BUY, request);
	}

	private CreateOfferResult createOffer(User user, DexTaskTypeEnum taskType, CreateOfferRequest request) throws TokenMetaNotFoundException, SigningException, StellarException, AssetConvertException, EffectiveAmountIsNegativeException, TradeWalletCreateFailedException, TradeWalletRebalanceException {
		final DexTaskId dexTaskId = DexTaskId.generate_taskId(taskType);
		final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
		final long userId = user.getId();
		final BigDecimal amount = StellarConverter.scale(request.getAmount());
		final BigDecimal price = request.getPrice();
		final boolean feeByTalk = request.getFeeByTalk();

		String position;

		// create task record
		DexTaskCreateofferRecord taskRecord = new DexTaskCreateofferRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);
		taskRecord.setTasktype(taskType);
		taskRecord.setSourceaccount(tradeWallet.getAccountId());
		taskRecord.setSellassetcode(request.getSellAssetCode());
		taskRecord.setBuyassetcode(request.getBuyAssetCode());
		taskRecord.setAmountraw(StellarConverter.actualToRaw(amount).longValueExact());
		taskRecord.setPrice(price);
		taskRecord.setFeebytalk(feeByTalk);
		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);


		position = "calc_fee";
		CalculateFeeResult feeResult;
		try {
			// calculate fee
			feeResult = feeCalculationService.calculateOfferFee(!taskType.equals(DexTaskTypeEnum.OFFER_CREATE_BUY), request.getSellAssetCode(), request.getBuyAssetCode(), amount, price, feeByTalk);
			// set amount log
			taskRecord.setSellamountraw(feeResult.getSellAmountRaw().longValueExact());
			taskRecord.setBuyamountraw(feeResult.getBuyAmountRaw().longValueExact());
			taskRecord.setFeeassetcode(feeResult.getFeeAssetCode());
			taskRecord.setFeeamountraw(feeResult.getFeeAmountRaw().longValueExact());
			taskRecord.setFeecollectaccount(feeResult.getFeeHolderAccountAddress());
		} catch(TalkenException tex) {
			DexTaskRecord.writeError(taskRecord, position, tex);
			throw tex;
		}


		// Adjust native balance before offer
		position = "rebalance";
		try {
			StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();

			ObjectPair<Boolean, BigDecimal> rebalanced;
			if(feeByTalk) {
				rebalanced = twService.addNativeBalancingOperation(sctxBuilder, tradeWallet, true, request.getSellAssetCode(), request.getBuyAssetCode(), "TALK");
			} else {
				rebalanced = twService.addNativeBalancingOperation(sctxBuilder, tradeWallet, true, request.getSellAssetCode(), request.getBuyAssetCode());
			}

			if(rebalanced.first()) {
				try {
					logger.debug("Rebalance trade wallet {} (#{}) for offer task.", tradeWallet.getAccountId(), userId);
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

		position = "build_tx";
		StellarChannelTransaction.Builder sctxBuilder;
		try {
			final Asset sellAssetType = tmService.getAssetType(request.getSellAssetCode());
			final Asset buyAssetType = tmService.getAssetType(request.getBuyAssetCode());

			sctxBuilder = stellarNetworkService.newChannelTxBuilder()
					.setMemo(dexTaskId.getId())
					.addSigner(new StellarSignerAccount(twService.extractKeyPair(tradeWallet)));

			// build fee operation
			if(feeResult.getFeeAmountRaw().compareTo(BigInteger.ZERO) > 0) {
				sctxBuilder.addOperation(
						new PaymentOperation
								.Builder(feeResult.getFeeHolderAccountAddress(), feeResult.getFeeAssetType(), StellarConverter.rawToActualString(feeResult.getFeeAmountRaw()))
								.setSourceAccount(tradeWallet.getAccountId())
								.build()
				);
			}

			// build manage offer operation
			switch(taskType) {
				case OFFER_CREATE_SELL:
					sctxBuilder.addOperation(
							new ManageSellOfferOperation
									.Builder(sellAssetType, buyAssetType, StellarConverter.rawToActualString(feeResult.getSellAmountRaw()), StellarConverter.actualToString(price))
									.setOfferId(0)
									.setSourceAccount(tradeWallet.getAccountId())
									.build()
					);
					break;
				case OFFER_CREATE_BUY:
					sctxBuilder.addOperation(
							new ManageBuyOfferOperation
									.Builder(sellAssetType, buyAssetType, StellarConverter.rawToActualString(feeResult.getBuyAmountRaw()), StellarConverter.actualToString(price))
									.setOfferId(0)
									.setSourceAccount(tradeWallet.getAccountId())
									.build()
					);
					break;
				default:
					throw new IllegalArgumentException("TaskType " + taskType + " is not supported by createOffer");
			}
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

		CreateOfferResult result = new CreateOfferResult();
		result.setTaskId(dexTaskId.getId());
		result.setTaskType(taskType);
		result.setSellAssetCode(taskRecord.getSellassetcode());
		result.setBuyAssetCode(taskRecord.getBuyassetcode());
		result.setAmount(amount);
		result.setPrice(price);
		result.setFeeResult(feeResult);
		return result;
	}


	public DeleteOfferResult deleteSellOffer(User user, DeleteOfferRequest request) throws SigningException, TokenMetaNotFoundException, TradeWalletCreateFailedException, StellarException {
		return deleteOffer(user, DexTaskTypeEnum.OFFER_DELETE_SELL, request);
	}

	public DeleteOfferResult deleteBuyOffer(User user, DeleteOfferRequest request) throws SigningException, TokenMetaNotFoundException, TradeWalletCreateFailedException, StellarException {
		return deleteOffer(user, DexTaskTypeEnum.OFFER_DELETE_BUY, request);
	}

	private DeleteOfferResult deleteOffer(User user, DexTaskTypeEnum taskType, DeleteOfferRequest request) throws TokenMetaNotFoundException, StellarException, SigningException, TradeWalletCreateFailedException {
		final DexTaskId dexTaskId = DexTaskId.generate_taskId(taskType);
		final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
		final long userId = user.getId();
		final BigDecimal price = request.getPrice();
		final long offerId = request.getOfferId();

		String position;

		// create task record
		DexTaskDeleteofferRecord taskRecord = new DexTaskDeleteofferRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);
		taskRecord.setTasktype(taskType);
		taskRecord.setSourceaccount(tradeWallet.getAccountId());
		taskRecord.setOfferid(offerId);
		taskRecord.setSellassetcode(request.getSellAssetCode());
		taskRecord.setBuyassetcode(request.getBuyAssetCode());
		taskRecord.setPrice(price);
		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);


		Optional<DexTxmonCreateofferRecord> opt_dexCreateOfferResultRecord = dslContext.selectFrom(DEX_TXMON_CREATEOFFER)
				.where(DEX_TXMON_CREATEOFFER.OFFERID.eq(offerId))
				.fetchOptional();

		if(opt_dexCreateOfferResultRecord.isPresent()) {
			taskRecord.setCreateofferTaskid(opt_dexCreateOfferResultRecord.get().getTaskidCrof());
		} else {
			// TODO : determine what to do, force proceed? or drop
			logger.warn("Create offer result for {} not found, this may cause unexpected refund result.");
		}

		position = "build_tx";
		StellarChannelTransaction.Builder sctxBuilder;
		try {
			final Asset sellAssetType = tmService.getAssetType(request.getSellAssetCode());
			final Asset buyAssetType = tmService.getAssetType(request.getBuyAssetCode());

			sctxBuilder = stellarNetworkService.newChannelTxBuilder()
					.setMemo(dexTaskId.getId())
					.addSigner(new StellarSignerAccount(twService.extractKeyPair(tradeWallet)));

			// build manage offer operation
			switch(taskType) {
				case OFFER_DELETE_SELL:
					sctxBuilder.addOperation(
							new ManageSellOfferOperation
									.Builder(sellAssetType, buyAssetType, "0", StellarConverter.actualToString(price))
									.setOfferId(offerId)
									.setSourceAccount(tradeWallet.getAccountId())
									.build()
					);
					break;
				case OFFER_DELETE_BUY:
					sctxBuilder.addOperation(
							new ManageBuyOfferOperation
									.Builder(sellAssetType, buyAssetType, "0", StellarConverter.actualToString(price))
									.setOfferId(offerId)
									.setSourceAccount(tradeWallet.getAccountId())
									.build()
					);
					break;
				default:
					throw new IllegalArgumentException("TaskType " + taskType + " is not supported by deleteOffer");
			}
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

		DeleteOfferResult result = new DeleteOfferResult();
		result.setTaskId(dexTaskId.getId());
		result.setTaskType(taskType);
		result.setOfferId(taskRecord.getOfferid());
		result.setSellAssetCode(taskRecord.getSellassetcode());
		result.setBuyAssetCode(taskRecord.getBuyassetcode());
		result.setPrice(price);
		return result;
	}
}
