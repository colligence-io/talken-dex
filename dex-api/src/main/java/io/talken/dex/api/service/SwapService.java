package io.talken.dex.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.PostLaunchExecutor;
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
import io.talken.dex.shared.TokenMetaTable;
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

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.util.Map;

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

	@PostConstruct
	private void test() {
		PostLaunchExecutor.addTask(() -> {
			try {
				getSwapResultPrediction("TALK", BigDecimal.valueOf(200), "ETH");
			} catch(Exception ex) {
				logger.exception(ex);
			}
		});
	}

	public SwapResult swap(long userId, SwapRequest request) throws SwapPredictionThresholdException, SwapServiceNotAvailableException, SwapPathNotAvailableException, TokenMetaNotFoundException, SwapUnderMinimumAmountException, StellarException, IntegrationException, ActiveAssetHolderAccountNotFoundException, BlockChainPlatformNotSupportedException, InternalServerErrorException {
		// get meta data for source
		BlockChainPlatformEnum sourceBctxPlatform = tmService.getTokenBctxPlatform(request.getSourceAssetCode());
		TokenMetaTable.Meta tokenMeta = tmService.getTokenMeta(request.getSourceAssetCode());

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
		if(reqTargetAmount.compareTo(prediction.getPrediction()) > 0) {
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
		taskRecord.setPredictamountraw(StellarConverter.actualToRaw(prediction.getAmount()).longValueExact());

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
		dslContext.attach(taskRecord);
		taskRecord.store();
		logger.info("{} generated. userId = {}", dexTaskId, userId);

		// build relay contents
		RelayEncryptedContent<RelayTransferDTO> encData;
		try {
			RelayTransferDTO encDto = new RelayTransferDTO();
			encDto.setPlatform(sourceBctxPlatform);
			encDto.setWalletType(sourceBctxPlatform.getWalletType());
			encDto.setSignType(sourceBctxPlatform.getWalletType().getSignType());
			encDto.setSymbol(request.getSourceAssetCode());
			if(tokenMeta.getAux() != null) {
				for(Map.Entry<TokenMetaAuxCodeEnum, Object> auxEntry : tokenMeta.getAux().entrySet()) {
					if(auxEntry.getKey().equals(TokenMetaAuxCodeEnum.TOKEN_CARD_THEME_COLOR))
						encDto.getAux().put(auxEntry.getKey().name(), auxEntry.getValue());
				}
			}
			encDto.setFrom(request.getPrivateSourceAddr());
			encDto.setTo(holderAddr);
			encDto.setAmount(maxSourceAmount);
			encDto.setNetfee(request.getNetworkFee());
			encDto.setMemo(dexTaskId.getId());

			encData = new RelayEncryptedContent<>(encDto);
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

			taskRecord.setErrorposition("encrypt relay data");
			taskRecord.setErrorcode(e.getClass().getSimpleName());
			taskRecord.setErrormessage(e.getMessage());
			taskRecord.setFinishFlag(true);
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new InternalServerErrorException(e);
		}

		// send relay addContents request
		IntegrationResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.SWAP, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {} {}", dexTaskId, relayResult.getErrorCode(), relayResult.getErrorMessage());

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(relayResult.getErrorCode());
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setFinishFlag(true);
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new IntegrationException(relayResult);
		}

		// update task record
		taskRecord.setRlyDexkey(encData.getKey());
		taskRecord.setRlyTransid(relayResult.getData().getTransId());
		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
		taskRecord.update();

		logger.debug("{} complete.", dexTaskId);

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

	public DexKeyResult swapDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.SWAP_ANCHOR))
			throw new TaskNotFoundException(taskId);

		DexTaskSwapRecord taskRecord = dslContext.selectFrom(DEX_TASK_SWAP)
				.where(DEX_TASK_SWAP.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getTradeaddr(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

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
