package io.talken.dex.api.service;


import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.GeneralException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.DexTaskRecord;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskCreateofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeleteofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskRefundcreateofferfeeRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TransactionBlockExecutor;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;
import org.stellar.sdk.ManageBuyOfferOperation;
import org.stellar.sdk.ManageSellOfferOperation;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.xdr.OfferEntry;
import org.stellar.sdk.xdr.OperationResult;
import org.stellar.sdk.xdr.TransactionResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_CREATEOFFER;

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
	private final DataSourceTransactionManager txMgr;

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
		SubmitTransactionResponse txResponse;
		// put tx info and submit tx
		try(StellarChannelTransaction sctx = sctxBuilder.build()) {
			taskRecord.setTxSeq(sctx.getTx().getSequenceNumber());
			taskRecord.setTxHash(ByteArrayUtil.toHexString(sctx.getTx().hash()));
			taskRecord.setTxXdr(sctx.getTx().toEnvelopeXdrBase64());
			taskRecord.store();

			position = "submit_sctx";
			txResponse = sctx.submit();

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

		position = "process_result";
		boolean postTxStatus;
		try {
			TransactionResult transactionResult = txResponse.getDecodedTransactionResult().get();
			// extract offerEntry from result
			OfferEntry offerEntry = null;
			for(OperationResult operationResult : transactionResult.getResult().getResults()) {
				switch(operationResult.getTr().getDiscriminant()) {
					case MANAGE_SELL_OFFER:
						offerEntry = operationResult.getTr().getManageSellOfferResult().getSuccess().getOffer().getOffer();
						break;
					case MANAGE_BUY_OFFER:
						offerEntry = operationResult.getTr().getManageSellOfferResult().getSuccess().getOffer().getOffer();
						break;
				}
				if(offerEntry != null) break;
			}

			if(offerEntry != null) { // offer made (otherwise all request is claimed)
				final long offerId = offerEntry.getOfferID().getInt64();
				final long madeSellAmount = offerEntry.getAmount().getInt64();

				// update taskLog
				taskRecord.setOfferid(offerId);
				taskRecord.setMadeamountraw(madeSellAmount);
				taskRecord.store();

				if(feeResult.getFeeAmountRaw().compareTo(BigInteger.ZERO) > 0) {
					// feeResult.getSellAmountRaw() : madeSellAmount = feeResult.getFeeAmountRaw() : refundAmount
					// ma * fa = sa * ra
					// ra = (ma * fa) / sa

					BigInteger refundAmountRaw = feeResult.getFeeAmountRaw().multiply(BigInteger.valueOf(madeSellAmount)).divide(feeResult.getSellAmountRaw());

					// if refundAmount is bigger than zero, insert new refund task
					if(refundAmountRaw.compareTo(BigInteger.ZERO) > 0) {
						DexTaskRefundcreateofferfeeRecord refundRecord = new DexTaskRefundcreateofferfeeRecord();
						DexTaskId refundTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.OFFER_REFUNDFEE);
						refundRecord.setTaskid(refundTaskId.getId());
						refundRecord.setTaskidCrof(dexTaskId.getId());
						refundRecord.setUserId(userId);
						refundRecord.setFeecollectaccount(taskRecord.getFeecollectaccount());
						refundRecord.setRefundassetcode(taskRecord.getFeeassetcode());
						refundRecord.setRefundamountraw(refundAmountRaw.longValueExact());
						refundRecord.setRefundaccount(taskRecord.getSourceaccount());
						dslContext.attach(refundRecord);

						BctxRecord bctxRecord = new BctxRecord();
						bctxRecord.setBctxType(BlockChainPlatformEnum.STELLAR_TOKEN);
						TokenMetaTable.Meta tokenMeta = tmService.getTokenMeta(taskRecord.getFeeassetcode());
						bctxRecord.setSymbol(tokenMeta.getManagedInfo().getAssetCode());
						bctxRecord.setPlatformAux(tokenMeta.getManagedInfo().getIssuerAddress());
						bctxRecord.setAddressFrom(taskRecord.getFeecollectaccount());
						bctxRecord.setAddressTo(taskRecord.getSourceaccount());
						bctxRecord.setAmount(StellarConverter.rawToActual(refundAmountRaw));
						bctxRecord.setNetfee(BigDecimal.ZERO);
						bctxRecord.setTxAux(refundTaskId.getId());
						dslContext.attach(bctxRecord);

						TransactionBlockExecutor.of(txMgr).transactional(() -> {
							bctxRecord.store();
							refundRecord.setBctxId(bctxRecord.getId());
							refundRecord.store();
						});
					}
				}
			}
			postTxStatus = true;
		} catch(Exception ex) {
			TalkenException tex;
			if(ex instanceof TalkenException) tex = (TalkenException) ex;
			else tex = new GeneralException(ex);
			DexTaskRecord.writeError(taskRecord, position, tex);
			// and do not throw exception, leave cleaning mess with C/S, user will get success result
			postTxStatus = false;
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
		if(taskRecord.getOfferid() != null)
			result.setOfferId(taskRecord.getOfferid());
		if(taskRecord.getMadeamountraw() != null)
			result.setMadeAmount(StellarConverter.rawToActual(taskRecord.getMadeamountraw()));
		result.setPostTxStatus(postTxStatus);
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

		Optional<DexTaskCreateofferRecord> opt_dexCreateOfferResultRecord = dslContext.selectFrom(DEX_TASK_CREATEOFFER)
				.where(DEX_TASK_CREATEOFFER.OFFERID.eq(offerId))
				.fetchOptional();

		if(opt_dexCreateOfferResultRecord.isPresent()) {
			taskRecord.setCreateofferTaskid(opt_dexCreateOfferResultRecord.get().getTaskid());
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
