package io.talken.dex.api.service;


import ch.qos.logback.core.encoder.ByteArrayUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskAnchorRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeanchorRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.integration.relay.RelayAddContentsResponse;
import io.talken.dex.api.service.integration.relay.RelayEncryptedContent;
import io.talken.dex.api.service.integration.relay.RelayMsgTypeEnum;
import io.talken.dex.api.service.integration.relay.RelayServerService;
import io.talken.dex.api.service.integration.relay.dto.RelayStellarRawTxDTO;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.*;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.blockchain.stellar.StellarSignVerifier;
import io.talken.dex.shared.service.integration.anchor.*;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.GeneralSecurityException;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_ANCHOR;
import static io.talken.common.persistence.jooq.Tables.DEX_TASK_DEANCHOR;


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
	private DSLContext dslContext;

	public AnchorResult anchor(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, BigDecimal amount, AnchorRequest ancRequestBody) throws TokenMetaNotFoundException, IntegrationException, ActiveAssetHolderAccountNotFoundException, InternalServerErrorException {
		String assetHolderAddress = tmService.getActiveHolderAccountAddress(assetCode);
		ancRequestBody.setTo(assetHolderAddress);

		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.ANCHOR);

		// create task record
		DexTaskAnchorRecord taskRecord = new DexTaskAnchorRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setPrivateaddr(privateWalletAddress);
		taskRecord.setTradeaddr(tradeWalletAddress);
		taskRecord.setHolderaddr(assetHolderAddress);
		taskRecord.setAssetcode(assetCode);
		taskRecord.setAmountraw(StellarConverter.actualToRaw(amount).longValueExact());

		AncServerAnchorRequest anchor_request = new AncServerAnchorRequest();
		anchor_request.setTaskId(dexTaskId.getId());
		anchor_request.setUid(String.valueOf(userId));
		anchor_request.setFrom(privateWalletAddress);
		anchor_request.setTo(assetHolderAddress);
		anchor_request.setStellar(tradeWalletAddress);
		anchor_request.setSymbol(assetCode);
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

		// build relay contents
		RelayEncryptedContent<AnchorRequest> encData;
		try {
			encData = new RelayEncryptedContent<>(ancRequestBody);
			// set trans description
			encData.addDescription("privateWalletAddress", taskRecord.getPrivateaddr());
			encData.addDescription("tradeWalletAddress", taskRecord.getTradeaddr());
			encData.addDescription("assetHolderAddress", taskRecord.getHolderaddr());
			encData.addDescription("assetCode", taskRecord.getAssetcode());
			encData.addDescription("amount", StellarConverter.rawToActualString(BigInteger.valueOf(taskRecord.getAmountraw())));
		} catch(JsonProcessingException | GeneralSecurityException e) {
			logger.error("{} failed. {} {}", dexTaskId, e.getClass().getSimpleName(), e.getMessage());

			taskRecord.setErrorposition("encrypt relay data");
			taskRecord.setErrorcode(e.getClass().getSimpleName());
			taskRecord.setErrormessage(e.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new InternalServerErrorException(e);
		}

		// send relay addContents request
		IntegrationResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.ANCHOR, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {} {}", dexTaskId, relayResult.getErrorCode(), relayResult.getErrorMessage());

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(relayResult.getErrorCode());
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new IntegrationException(relayResult);
		}

		// update task record
		taskRecord.setRlyDexkey(encData.getKey());
		taskRecord.setRlyTransid(relayResult.getData().getTransId());
		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
		taskRecord.setSuccessFlag(true);
		taskRecord.update();

		logger.debug("{} complete.", dexTaskId);

		AnchorResult result = new AnchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setHolderAccountAddress(assetHolderAddress);
		result.setTransId(taskRecord.getRlyTransid());
		result.setAssetCode(taskRecord.getAssetcode());
		result.setAmount(StellarConverter.rawToActual(taskRecord.getAmountraw()));
		return result;
	}

	public DexKeyResult anchorDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.ANCHOR))
			throw new TaskNotFoundException(taskId);

		DexTaskAnchorRecord taskRecord = dslContext.selectFrom(DEX_TASK_ANCHOR)
				.where(DEX_TASK_ANCHOR.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getTradeaddr(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getRlyDexkey());
		return result;
	}

	public DeanchorResult deanchor(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, BigDecimal amount, Boolean feeByTalk) throws TokenMetaNotFoundException, StellarException, AssetConvertException, EffectiveAmountIsNegativeException, IntegrationException, InternalServerErrorException {
		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.DEANCHOR);

		amount = StellarConverter.scale(amount);

		// create task record
		DexTaskDeanchorRecord taskRecord = new DexTaskDeanchorRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setPrivateaddr(privateWalletAddress);
		taskRecord.setTradeaddr(tradeWalletAddress);
		taskRecord.setAssetcode(assetCode);
		taskRecord.setAmountraw(StellarConverter.actualToRaw(amount).longValueExact());
		taskRecord.setFeebyctx(feeByTalk);

		// calculate fee
		CalculateFeeResult feeResult = feeCalculationService.calculateDeanchorFee(assetCode, amount, feeByTalk);

		// build raw tx
		Transaction rawTx;
		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(tradeWalletAddress);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source.getAccountId());

			KeyPair baseAccount = tmService.getBaseAccount(assetCode);

			Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount, stellarNetworkService.getNetwork())
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.setOperationFee(stellarNetworkService.getNetworkFee())
					.addMemo(Memo.text(dexTaskId.getId()));

			// build fee operation
			if(feeResult.getFeeAmountRaw().compareTo(BigInteger.ZERO) > 0) {
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(feeResult.getFeeHolderAccountAddress(), feeResult.getFeeAssetType(), StellarConverter.rawToActualString(feeResult.getFeeAmountRaw()))
								.build()
				);
			}

			// build deanchor operation
			txBuilder.addOperation(
					new PaymentOperation
							.Builder(baseAccount.getAccountId(), feeResult.getSellAssetType(), StellarConverter.rawToActualString(feeResult.getSellAmountRaw()))
							.build()
			);

			rawTx = txBuilder.build();

			taskRecord.setBaseaccount(baseAccount.getAccountId());
			taskRecord.setDeanchoramountraw(feeResult.getSellAmountRaw().longValueExact());
			taskRecord.setFeeamountraw(feeResult.getFeeAmountRaw().longValueExact());
			taskRecord.setFeeassettype(StellarConverter.toAssetCode(feeResult.getFeeAssetType()));
			taskRecord.setFeecollectaccount(feeResult.getFeeHolderAccountAddress());
			taskRecord.setTxSeq(rawTx.getSequenceNumber());
			taskRecord.setTxHash(ByteArrayUtil.toHexString(rawTx.hash()));
			taskRecord.setTxXdr(rawTx.toEnvelopeXdrBase64());
		} catch(Exception ex) {
			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());
			throw new StellarException(ex);
		}

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
		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);


		// build relay contents
		RelayEncryptedContent<RelayStellarRawTxDTO> encData;
		try {
			encData = new RelayEncryptedContent<>(new RelayStellarRawTxDTO(rawTx));
			// set trans description
			encData.addDescription("privateWalletAddress", taskRecord.getPrivateaddr());
			encData.addDescription("tradeWalletAddress", taskRecord.getTradeaddr());
			encData.addDescription("baseAccountAddress", taskRecord.getBaseaccount());
			encData.addDescription("assetCode", taskRecord.getAssetcode());
			encData.addDescription("amount", StellarConverter.rawToActualString(taskRecord.getAmountraw()));
			encData.addDescription("feeAssetCode", taskRecord.getFeeassettype());
			encData.addDescription("feeAmount", StellarConverter.rawToActualString(taskRecord.getFeeamountraw()));
		} catch(JsonProcessingException | GeneralSecurityException e) {
			logger.error("{} failed. {} {}", dexTaskId, e.getClass().getSimpleName(), e.getMessage());

			taskRecord.setErrorposition("encrypt relay data");
			taskRecord.setErrorcode(e.getClass().getSimpleName());
			taskRecord.setErrormessage(e.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new InternalServerErrorException(e);
		}

		// send relay addContents request
		IntegrationResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.DEANCHOR, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {} {}", dexTaskId, relayResult.getErrorCode(), relayResult.getErrorMessage());

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(relayResult.getErrorCode());
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new IntegrationException(relayResult);
		}

		// update task record
		taskRecord.setRlyDexkey(encData.getKey());
		taskRecord.setRlyTransid(relayResult.getData().getTransId());
		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
		taskRecord.setSuccessFlag(true);
		taskRecord.update();

		logger.debug("{} complete.", dexTaskId);

		DeanchorResult result = new DeanchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setTransId(taskRecord.getRlyTransid());
		result.setFeeAssetCode(taskRecord.getFeeassettype());
		result.setFeeAmount(StellarConverter.rawToActual(taskRecord.getFeeamountraw()));
		result.setDeanchorAssetCode(taskRecord.getAssetcode());
		result.setDeanchorAmount(StellarConverter.rawToActual(taskRecord.getDeanchoramountraw()));
		return result;
	}

	public DexKeyResult deanchorDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.DEANCHOR))
			throw new TaskNotFoundException(taskId);

		DexTaskDeanchorRecord taskRecord = dslContext.selectFrom(DEX_TASK_DEANCHOR)
				.where(DEX_TASK_DEANCHOR.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getTradeaddr(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getRlyDexkey());
		return result;
	}
}
