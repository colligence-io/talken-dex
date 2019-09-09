package io.talken.dex.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.integration.relay.RelayAddContentsResponse;
import io.talken.dex.api.service.integration.relay.RelayEncryptedContent;
import io.talken.dex.api.service.integration.relay.RelayMsgTypeEnum;
import io.talken.dex.api.service.integration.relay.RelayServerService;
import io.talken.dex.api.service.integration.relay.dto.RelayTransferDTO;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.*;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarSignVerifier;
import io.talken.dex.shared.service.integration.anchor.AncServerAnchorRequest;
import io.talken.dex.shared.service.integration.anchor.AncServerAnchorResponse;
import io.talken.dex.shared.service.integration.anchor.AnchorServerService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.GeneralSecurityException;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_SWAP;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class SwapService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SwapService.class);

	// constructor injection
	private final AnchorServerService anchorServerService;
	private final RelayServerService relayServerService;
	private final SwapTableService swapTableService;
	private final TokenMetaService tmService;
	private final FeeCalculationService feeCalculationService;
	private final DSLContext dslContext;

	private static final String pivotAssetCode = "USDT";

	public SwapResult swap(long userId, SwapRequest request) throws SwapPredictionThresholdException, SwapServiceNotAvailableException, SwapPathNotAvailableException, TokenMetaNotFoundException, SwapUnderMinimumAmountException, StellarException, IntegrationException, ActiveAssetHolderAccountNotFoundException, BlockChainPlatformNotSupportedException, InternalServerErrorException {
		// prepare relay dto (also check tokenmeta and platform meta)
		RelayTransferDTO relayTransferDTO = relayServerService.createTransferDTObase(request.getSourceAssetCode());

		// scale input
		BigDecimal maxSourceAmount = StellarConverter.scale(request.getSourceAmount());
		BigDecimal reqTargetAmount = StellarConverter.scale(request.getTargetAmount());

		// generate dexTaskId
		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.SWAP_ANCHOR);

		// calculate prediction and throw exception if smaller than requested
		SwapPredictionResult prediction;
		try {
			prediction = getSwapResultPrediction(request.getSourceAssetCode(), maxSourceAmount, request.getTargetAssetCode());
		} catch(SwapPathNotAvailableException ex) {
			// rebuild swappath exception to source->target, so user cannot notice pivot path
			throw new SwapPathNotAvailableException(request.getSourceAssetCode(), request.getTargetAssetCode(), request.getSourceAmount());
		}

		// if prediction is more than requested, this means sourceAsset will be remain more than expected. => more PROFIT expected
		if(prediction.getPrediction().compareTo(reqTargetAmount) > 0) {
			// do nothing
		}
		// if prediction is less than requested, this means sourceAsset will be remain less than expected. => less PROFIT expected
		else if(prediction.getPrediction().compareTo(reqTargetAmount) < 0) {
			// TODO : decide we take disadvantage of less profit, or just reject swap request
			// FIXME : WE WILL NOT TAKE LESS PROFIT
			throw new SwapPredictionThresholdException(request.getSourceAssetCode(), request.getTargetAssetCode(), reqTargetAmount, prediction.getPrediction());
		}

		// get service account addresses
		String holderAddr = tmService.getActiveHolderAccountAddress(request.getSourceAssetCode());
		String swapperAddr = pickSwapAccount();

		// create task record
		DexTaskSwapRecord taskRecord = new DexTaskSwapRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);
		taskRecord.setPrivatesourceaddr(request.getPrivateSourceAddr());
		taskRecord.setSourceassetcode(request.getSourceAssetCode());
		taskRecord.setSourceamountraw(StellarConverter.actualToRaw(maxSourceAmount).longValueExact());
		taskRecord.setPrivatetargetaddr(request.getPrivateTargetAddr());
		taskRecord.setTargetassetcode(request.getTargetAssetCode());
		taskRecord.setTargetamountraw(StellarConverter.actualToRaw(reqTargetAmount).longValueExact());
		taskRecord.setTradeaddr(request.getTradeAddr());
		taskRecord.setHolderaddr(holderAddr);
		taskRecord.setSwapperaddr(swapperAddr);
		taskRecord.setPredicttargetamountraw(StellarConverter.actualToRaw(prediction.getAmount()).longValueExact());

		// build anchor request
		AncServerAnchorRequest anchor_request = new AncServerAnchorRequest();
		anchor_request.setTaskId(dexTaskId.getId());
		anchor_request.setUid(String.valueOf(userId));
		anchor_request.setFrom(request.getPrivateSourceAddr());
		anchor_request.setTo(holderAddr);
		anchor_request.setStellar(swapperAddr);
		anchor_request.setSymbol(request.getSourceAssetCode());
		anchor_request.setValue(maxSourceAmount);
		anchor_request.setMemo(UTCUtil.getNow().toString());

		// request anchor monitor
		IntegrationResult<AncServerAnchorResponse> anchorResult = anchorServerService.requestAnchor(anchor_request);
		if(!anchorResult.isSuccess()) {
			throw new IntegrationException(anchorResult);
		}

		// insert task record
		taskRecord.setStatus(DexSwapStatusEnum.ANCHOR_REQUESTED);
		taskRecord.setAncIndex(anchorResult.getData().getData().getIndex());

		// build relay contents
		RelayEncryptedContent<RelayTransferDTO> encData;
		try {
			relayTransferDTO.setFrom(request.getPrivateSourceAddr());
			relayTransferDTO.setTo(holderAddr);
			relayTransferDTO.setAmount(maxSourceAmount);
			relayTransferDTO.setNetfee(request.getNetworkFee());
			relayTransferDTO.setMemo(dexTaskId.getId());

			encData = new RelayEncryptedContent<>(relayTransferDTO);
			// set trans description
			encData.addDescription("privateSourceWalletAddress", request.getPrivateSourceAddr());
			encData.addDescription("privateTargetWalletAddress", request.getPrivateTargetAddr());
			encData.addDescription("sourceAssetCode", request.getSourceAssetCode());
			encData.addDescription("targetAssetCode", request.getTargetAssetCode());
			encData.addDescription("sourceAmount", StellarConverter.actualToString(maxSourceAmount));
			encData.addDescription("targetAmount", StellarConverter.actualToString(reqTargetAmount));
			encData.addDescription("networkFee", request.getNetworkFee().stripTrailingZeros().toPlainString());
			encData.addDescription("tradeWalletAddress", request.getTradeAddr());
			encData.addDescription("assetHolderAddress", holderAddr);
			encData.addDescription("swapperAddress", swapperAddr);

		} catch(JsonProcessingException | GeneralSecurityException e) {
			logger.error("{} failed. {} {}", dexTaskId, e.getClass().getSimpleName(), e.getMessage());
			throw new InternalServerErrorException(e);
		}

		// send relay addContents request
		IntegrationResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.SWAP, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {} {}", dexTaskId, relayResult.getErrorCode(), relayResult.getErrorMessage());
			throw new IntegrationException(relayResult);
		}

		// insert task record
		taskRecord.setRlyDexkey(encData.getKey());
		taskRecord.setRlyTransid(relayResult.getData().getTransId());
		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);

		SwapResult result = new SwapResult();
		result.setTaskId(dexTaskId.getId());
		result.setTransId(taskRecord.getRlyTransid());
		result.setSourceAssetCode(taskRecord.getSourceassetcode());
		result.setSourceAmount(StellarConverter.rawToActual(taskRecord.getSourceamountraw()));
		result.setTargetAssetCode(taskRecord.getTargetassetcode());
		result.setTargetAmount(StellarConverter.rawToActual(taskRecord.getTargetamountraw()));
		result.setHolderAccountAddress(holderAddr);
		result.setSwapperAccountAddress(swapperAddr);
		return result;
	}

	public DexKeyResult swapDexKey(Long userId, DexKeyRequest request) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		final String taskId = request.getTaskId();

		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.SWAP_ANCHOR))
			throw new TaskNotFoundException(taskId);

		DexTaskSwapRecord taskRecord = dslContext.selectFrom(DEX_TASK_SWAP)
				.where(DEX_TASK_SWAP.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(request.getTransId())) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getTradeaddr(), request.getTransId(), request.getSignature()))
			throw new SignatureVerificationFailedException(request.getTransId(), request.getSignature());

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getRlyDexkey());
		return result;
	}

	private String pickSwapAccount() {
		return "GAAZIQRCZ2WKTKQ5DE62ZI63PGDWWXKVT3FUAFB5OTBQDHOZINQEUMLM";
	}

	public SwapPredictionResult getSwapResultPrediction(String sourceAssetCode, BigDecimal sourceAmount, String targetAssetCode) throws StellarException, TokenMetaNotFoundException, SwapPathNotAvailableException, SwapServiceNotAvailableException, SwapUnderMinimumAmountException {
		CalculateFeeResult feeResult = feeCalculationService.calculateSwapFee(sourceAssetCode, sourceAmount, targetAssetCode);

		// calculate target amount prediction
		BigDecimal swapAmount = sourceAmount.subtract(feeResult.getFeeAmount());
		BigDecimal prediction;

		if(targetAssetCode.equals(pivotAssetCode)) {
			prediction = swapTableService.getSwapTable(sourceAssetCode, targetAssetCode).predict(swapAmount);
		} else {
			BigDecimal pivotAmount = swapTableService.getSwapTable(sourceAssetCode, pivotAssetCode).predict(swapAmount);
			prediction = swapTableService.getSwapTable(pivotAssetCode, targetAssetCode).predict(pivotAmount);
		}

		prediction = StellarConverter.scale(prediction);

		SwapPredictionResult result = new SwapPredictionResult();
		result.setSourceAssetCode(sourceAssetCode);
		result.setTargetAssetCode(targetAssetCode);
		result.setAmount(sourceAmount);
		result.setFeeAmount(feeResult.getFeeAmount());
		result.setPrediction(prediction);

		logger.debug("SWAP prediction {} - {} {} -> {} {}", sourceAmount.stripTrailingZeros().toPlainString(), feeResult.getFeeAmount().stripTrailingZeros().toPlainString(), sourceAssetCode, prediction, targetAssetCode);

		return result;
	}
}
