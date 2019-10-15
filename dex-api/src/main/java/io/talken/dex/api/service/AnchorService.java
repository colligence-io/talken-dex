package io.talken.dex.api.service;


import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.DexTaskAnchorRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.dex.api.controller.dto.AnchorRequest;
import io.talken.dex.api.service.integration.PrivateWalletMsgTypeEnum;
import io.talken.dex.api.service.integration.PrivateWalletService;
import io.talken.dex.api.service.integration.PrivateWalletTransferDTO;
import io.talken.dex.api.service.integration.relay.RelayServerService;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.*;
import io.talken.dex.shared.service.blockchain.stellar.StellarChannelTransaction;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.integration.anchor.AncServerAnchorRequest;
import io.talken.dex.shared.service.integration.anchor.AncServerAnchorResponse;
import io.talken.dex.shared.service.integration.anchor.AnchorServerService;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.math.BigDecimal;

@Service
@Scope("singleton")
public class AnchorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorService.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private AnchorServerService anchorServerService;

	@Autowired
	private RelayServerService relayServerService;

	@Autowired
	private TokenMetaService tmService;

	@Autowired
	private FeeCalculationService feeCalculationService;

	@Autowired
	private TradeWalletService twService;

	@Autowired
	private PrivateWalletService pwService;

	@Autowired
	private DSLContext dslContext;

	public PrivateWalletTransferDTO anchor(User user, AnchorRequest request) throws TokenMetaNotFoundException, IntegrationException, ActiveAssetHolderAccountNotFoundException, InternalServerErrorException, BlockChainPlatformNotSupportedException, TradeWalletRebalanceException, TradeWalletCreateFailedException {
		final BigDecimal amount = StellarConverter.scale(request.getAmount());
		final DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.ANCHOR);
		final String assetHolderAddress = tmService.getActiveHolderAccountAddress(request.getAssetCode());
		final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
		final long userId = user.getId();

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

		// Adjust native balance before anchor
		StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();

		if(twService.addNativeBalancingOperation(sctxBuilder, tradeWallet, false, request.getAssetCode())) {
			try {
				logger.debug("Rebalance trade wallet {} (#{}) for anchor task.", tradeWallet.getAccountId(), userId);
				SubmitTransactionResponse rebalanceResponse = sctxBuilder.buildAndSubmit();
				if(!rebalanceResponse.isSuccess()) {
					ObjectPair<String, String> errorInfo = StellarConverter.getResultCodesFromExtra(rebalanceResponse);
					logger.error("Cannot rebalance trade wallet {} {} : {} {}", user.getId(), tradeWallet.getAccountId(), errorInfo.first(), errorInfo.second());
					throw new TradeWalletRebalanceException(errorInfo.first());
				}
			} catch(IOException | SigningException e) {
				logger.exception(e);
				throw new InternalServerErrorException(e);
			}
		}

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

		// insert task record
		taskRecord.setAncIndex(anchorResult.getData().getData().getIndex());
		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);

		PrivateWalletTransferDTO result = pwService.createTransferDTObase(PrivateWalletMsgTypeEnum.ANCHOR, taskRecord.getAssetcode());
		result.setAddrFrom(taskRecord.getPrivateaddr());
		result.setAddrTo(taskRecord.getHolderaddr());
		result.setAddrTrade(taskRecord.getTradeaddr());
		result.setAmount(StellarConverter.rawToActual(taskRecord.getAmountraw()));
		result.setNetfee(taskRecord.getNetworkfee());
		return result;
	}
//
//	public DeanchorResult deanchor(User user, DeanchorRequest request) throws TokenMetaNotFoundException, StellarException, AssetConvertException, EffectiveAmountIsNegativeException, IntegrationException, InternalServerErrorException, TradeWalletNotFoundException {
//		final BigDecimal amount = StellarConverter.scale(request.getAmount());
//		final boolean feeByTalk = Optional.ofNullable(request.getFeeByTalk()).orElse(false);
//		final DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.DEANCHOR);
//		final TradeWalletInfo tradeWallet = twService.loadTradeWallet(user);
//		final long userId = user.getId();
//
//		// create task record
//		DexTaskDeanchorRecord taskRecord = new DexTaskDeanchorRecord();
//		taskRecord.setTaskid(dexTaskId.getId());
//		taskRecord.setUserId(userId);
//
//		taskRecord.setPrivateaddr(request.getPrivateWalletAddress());
//		taskRecord.setTradeaddr(tradeWallet.getAccountId());
//		taskRecord.setAssetcode(request.getAssetCode());
//		taskRecord.setAmountraw(StellarConverter.actualToRaw(amount).longValueExact());
//		taskRecord.setNetworkfee(StellarConverter.rawToActual(stellarNetworkService.getNetworkFee()));
//		taskRecord.setFeebyctx(feeByTalk);
//
//		// calculate fee
//		CalculateFeeResult feeResult = feeCalculationService.calculateDeanchorFee(request.getAssetCode(), amount, feeByTalk);
//
//		// build raw tx
//		Transaction rawTx;
//		try {
//			// pick horizon server
//			Server server = stellarNetworkService.pickServer();
//
//			// prepare accounts
//			KeyPair source = KeyPair.fromAccountId(request.getTradeWalletAddress());
//
//			// load up-to-date information on source account.
//			AccountResponse sourceAccount = server.accounts().account(source.getAccountId());
//
//			KeyPair baseAccount = tmService.getBaseAccount(request.getAssetCode());
//
//			Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount, stellarNetworkService.getNetwork())
//					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
//					.setOperationFee(StellarConverter.actualToRaw(request.getNetworkFee()).intValueExact())
//					.addMemo(Memo.text(dexTaskId.getId()));
//
//			// build fee operation
//			if(feeResult.getFeeAmountRaw().compareTo(BigInteger.ZERO) > 0) {
//				txBuilder.addOperation(
//						new PaymentOperation
//								.Builder(feeResult.getFeeHolderAccountAddress(), feeResult.getFeeAssetType(), StellarConverter.rawToActualString(feeResult.getFeeAmountRaw()))
//								.build()
//				);
//			}
//
//			// build deanchor operation
//			txBuilder.addOperation(
//					new PaymentOperation
//							.Builder(baseAccount.getAccountId(), feeResult.getSellAssetType(), StellarConverter.rawToActualString(feeResult.getSellAmountRaw()))
//							.build()
//			);
//
//			rawTx = txBuilder.build();
//
//			taskRecord.setBaseaccount(baseAccount.getAccountId());
//			taskRecord.setDeanchoramountraw(feeResult.getSellAmountRaw().longValueExact());
//			taskRecord.setFeeamountraw(feeResult.getFeeAmountRaw().longValueExact());
//			taskRecord.setFeeassettype(StellarConverter.toAssetCode(feeResult.getFeeAssetType()));
//			taskRecord.setFeecollectaccount(feeResult.getFeeHolderAccountAddress());
//			taskRecord.setTxSeq(rawTx.getSequenceNumber());
//			taskRecord.setTxHash(ByteArrayUtil.toHexString(rawTx.hash()));
//			taskRecord.setTxXdr(rawTx.toEnvelopeXdrBase64());
//		} catch(Exception ex) {
//			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());
//			throw new StellarException(ex);
//		}
//
//		// request deanchor monitor
//		AncServerDeanchorRequest deanchor_request = new AncServerDeanchorRequest();
//		deanchor_request.setTaskId(dexTaskId.getId());
//		deanchor_request.setUid(String.valueOf(userId));
//		deanchor_request.setSymbol(taskRecord.getAssetcode());
//		deanchor_request.setHash(taskRecord.getTxHash());
//		deanchor_request.setFrom(taskRecord.getTradeaddr());
//		deanchor_request.setTo(taskRecord.getBaseaccount());
//		deanchor_request.setAddress(taskRecord.getPrivateaddr());
//		deanchor_request.setValue(StellarConverter.rawToActual(taskRecord.getDeanchoramountraw()).doubleValue());
//		deanchor_request.setMemo(UTCUtil.getNow().toString());
//
//		IntegrationResult<AncServerDeanchorResponse> deanchorResult = anchorServerService.requestDeanchor(deanchor_request);
//
//		if(!deanchorResult.isSuccess()) {
//			throw new IntegrationException(deanchorResult);
//		}
//
//		// insert task record
//		taskRecord.setAncIndex(deanchorResult.getData().getData().getIndex());
//		dslContext.attach(taskRecord);
//		taskRecord.store();
//		logger.info("{} generated. userId = {}", dexTaskId, userId);
//
//
//		// build relay contents
//		RelayEncryptedContent<RelayStellarRawTxDTO> encData;
//		try {
//			encData = new RelayEncryptedContent<>(new RelayStellarRawTxDTO(rawTx));
//			// set trans description
//			encData.addDescription("privateWalletAddress", taskRecord.getPrivateaddr());
//			encData.addDescription("tradeWalletAddress", taskRecord.getTradeaddr());
//			encData.addDescription("baseAccountAddress", taskRecord.getBaseaccount());
//			encData.addDescription("assetCode", taskRecord.getAssetcode());
//			encData.addDescription("amount", StellarConverter.rawToActualString(taskRecord.getAmountraw()));
//			encData.addDescription("feeAssetCode", taskRecord.getFeeassettype());
//			encData.addDescription("feeAmount", StellarConverter.rawToActualString(taskRecord.getFeeamountraw()));
//			encData.addDescription("networkFee", taskRecord.getNetworkfee().stripTrailingZeros().toPlainString());
//		} catch(GeneralSecurityException e) {
//			logger.error("{} failed. {} {}", dexTaskId, e.getClass().getSimpleName(), e.getMessage());
//
//			taskRecord.setErrorposition("encrypt relay data");
//			taskRecord.setErrorcode(e.getClass().getSimpleName());
//			taskRecord.setErrormessage(e.getMessage());
//			taskRecord.setSuccessFlag(false);
//			taskRecord.update();
//
//			throw new InternalServerErrorException(e);
//		}
//
//		// send relay addContents request
//		IntegrationResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.DEANCHOR, userId, dexTaskId, encData);
//
//		if(!relayResult.isSuccess()) {
//			logger.error("{} failed. {} {}", dexTaskId, relayResult.getErrorCode(), relayResult.getErrorMessage());
//
//			taskRecord.setErrorposition("request relay");
//			taskRecord.setErrorcode(relayResult.getErrorCode());
//			taskRecord.setErrormessage(relayResult.getErrorMessage());
//			taskRecord.setSuccessFlag(false);
//			taskRecord.update();
//
//			throw new IntegrationException(relayResult);
//		}
//
//		// update task record
//		taskRecord.setRlyDexkey(encData.getKey());
//		taskRecord.setRlyTransid(relayResult.getData().getTransId());
//		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
//		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
//		taskRecord.setSuccessFlag(true);
//		taskRecord.update();
//
//		logger.debug("{} complete.", dexTaskId);
//
//		DeanchorResult result = new DeanchorResult();
//		result.setTaskId(dexTaskId.getId());
//		result.setTransId(taskRecord.getRlyTransid());
//		result.setFeeAssetCode(taskRecord.getFeeassettype());
//		result.setFeeAmount(StellarConverter.rawToActual(taskRecord.getFeeamountraw()));
//		result.setDeanchorAssetCode(taskRecord.getAssetcode());
//		result.setDeanchorAmount(StellarConverter.rawToActual(taskRecord.getDeanchoramountraw()));
//		return result;
//	}
}
