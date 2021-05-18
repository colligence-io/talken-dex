package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.AnchorService;
import io.talken.dex.api.service.FeeCalculationService;
import io.talken.dex.api.service.WalletService;
import io.talken.dex.api.service.integration.PrivateWalletMsgTypeEnum;
import io.talken.dex.api.service.integration.PrivateWalletService;
import io.talken.dex.api.service.integration.PrivateWalletTransferDTO;
import io.talken.dex.shared.exception.TaskIntegrityCheckFailedException;
import io.talken.dex.shared.exception.TradeWalletCreateFailedException;
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * The type Wallet controller.
 */
@RestController
public class WalletController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(WalletController.class);

	@Autowired
	private AnchorService anchorService;

	@Autowired
	private FeeCalculationService feeService;

	@Autowired
	private PrivateWalletService pwService;

	@Autowired
	private WalletService walletService;

	@Autowired
	private AuthInfo authInfo;

    /**
     * get user trade wallet info
     *
     * @return trade wallet
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.TRADE_WALLET_BALANCE, method = RequestMethod.GET)
	public DexResponse<TradeWalletResult> getTradeWallet() throws TalkenException {
		return DexResponse.buildResponse(walletService.getTradeWalletBalances(authInfo.getUser()));
	}

    /**
     * build template tx envelope for TalkenWallet Mobile App
     *
     * @param assetCode the asset code
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.PRIVATE_WALLET_WITHDRAW_BASE, method = RequestMethod.GET)
	public DexResponse<PrivateWalletTransferDTO> withdraw_base(@RequestParam("assetCode") String assetCode) throws TalkenException {
		return DexResponse.buildResponse(pwService.createTransferDTObase(PrivateWalletMsgTypeEnum.TRANSFER, assetCode));
	}

    /**
     * anchor request
     *
     * @param postBody the post body
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.PRIVATE_WALLET_ANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<PrivateWalletTransferDTO> anchor(@RequestBody AnchorRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.anchor(authInfo.getUser(), postBody));
	}

    /**
     * deanchor request
     *
     * @param postBody the post body
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.TRADE_WALLET_DEANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<DeanchorResult> deanchor(@RequestBody DeanchorRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.deanchor(authInfo.getUser(), postBody));
	}

    /**
     * calculate deanchor fee
     *
     * @param postBody the post body
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @RequestMapping(value = RequestMappings.TRADE_WALLET_DEANCHOR_FEE, method = RequestMethod.POST)
	public DexResponse<CalculateFeeResult> deanchorFee(@RequestBody DeanchorRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(feeService.calculateDeanchorFee(postBody.getAssetCode(), postBody.getAmount()));
	}

    /**
     * search tradewallet tx list
     *
     * @param address       the address
     * @param operationType the operation type
     * @param assetCode     the asset code
     * @param assetIssuer   the asset issuer
     * @param include       the include
     * @param sort          the sort
     * @param page          the page
     * @param offset        the offset
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @RequestMapping(value = RequestMappings.TRADE_WALLET_TXLIST, method = RequestMethod.GET)
	public DexResponse<List<StellarOpReceipt>> txList(
			@RequestParam(value = "address")
					String address,
			@RequestParam(value = "operationType", required = false)
					String operationType,
			@RequestParam(value = "assetCode", required = false)
					String assetCode,
			@RequestParam(value = "assetIssuer", required = false)
					String assetIssuer,
			@RequestParam(value = "include", required = false)
					String include,
			@RequestParam(value = "sort", defaultValue = "desc")
					String sort,
			@RequestParam(value = "page", defaultValue = "1")
					Integer page,
			@RequestParam(value = "offset", defaultValue = "10")
					Integer offset
	) throws TalkenException {
		Sort.Direction direction;
		if(sort == null || !sort.equalsIgnoreCase("asc")) direction = Sort.Direction.DESC;
		else direction = Sort.Direction.ASC;
		if(page == null) page = 1;
		if(offset == null) offset = 10;

		boolean includeAll = false;

		if(include != null && include.equalsIgnoreCase("all")) includeAll = true;

		return DexResponse.buildResponse(walletService.getTxList(address, operationType, assetCode, assetIssuer, includeAll, direction, page, offset));
	}

    /**
     * prepare luniverse transfer (refill gas LUK for user private wallet)
     *
     * @return dex response
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.PRIVATE_WALLET_PREPARE_LMT_TRANSFER, method = RequestMethod.GET)
	public DexResponse<Boolean> prepareLmtTransfer() throws TalkenException {
		return DexResponse.buildResponse(walletService.prepareTransferLuk(authInfo.getUser()));
	}

    /**
     * check user private luniverse wallet is ready to transfer asset
     *
     * @param address the address
     * @return dex response
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.PRIVATE_WALLET_CHECK_LMT_TRANSFER_READY, method = RequestMethod.GET)
	public DexResponse<Boolean> checkLmtTransferReady(@RequestParam("address") String address) {
		return DexResponse.buildResponse(walletService.checkTransferLukPrepared(address));
	}

    /**
     * Request reclaim dex response.
     *
     * @return the dex response
     * @throws TradeWalletCreateFailedException  the trade wallet create failed exception
     * @throws TaskIntegrityCheckFailedException the task integrity check failed exception
     * @throws TokenMetaNotFoundException        the token meta not found exception
     * @throws IntegrationException              the integration exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.TRADE_WALLET_RECLAIM, method = RequestMethod.GET)
    public DexResponse<ReclaimResult> requestReclaim() throws TradeWalletCreateFailedException, TaskIntegrityCheckFailedException, TokenMetaNotFoundException, IntegrationException {
        return DexResponse.buildResponse(walletService.getReclaimByUser(authInfo.getUser(), DexTaskTypeEnum.RECLAIM));
    }

    /**
     * Request reclaim dex response.
     *
     * @param postBody the post body
     * @return the dex response
     * @throws Exception the exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.TRADE_WALLET_RECLAIM, method = RequestMethod.POST)
    public DexResponse<ReclaimResult> requestReclaim(@RequestBody ReclaimRequest postBody) throws Exception {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(walletService.reclaim(authInfo.getUser(), postBody));
    }

    /**
     * Claim dex response.
     *
     * @return the dex response
     * @throws Exception the exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.TRADE_WALLET_CLAIM, method = RequestMethod.GET)
    public DexResponse<ReclaimResult> claim() throws Exception {
        return DexResponse.buildResponse(walletService.getReclaimByUser(authInfo.getUser(), DexTaskTypeEnum.CLAIM));
    }

    /**
     * Request claim dex response.
     *
     * @param postBody the post body
     * @return the dex response
     * @throws Exception the exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.TRADE_WALLET_CLAIM, method = RequestMethod.POST)
    public DexResponse<ClaimResult> requestClaim(@RequestBody ReclaimRequest postBody) throws Exception {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(walletService.claim(authInfo.getUser(), postBody));
    }

    /**
     * Anchor only talklmt dex response.
     *
     * @param postBody the post body
     * @return the dex response
     * @throws TalkenException the talken exception
     */
    @Deprecated
    @AuthRequired
    @RequestMapping(value = RequestMappings.PRIVATE_WALLET_TALK_LMT_ANCHOR, method = RequestMethod.POST)
    public DexResponse<PrivateWalletTransferDTO> anchorOnlyTALKLMT(@RequestBody AnchorRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(anchorService.anchorOnlyTALKLMT(authInfo.getUser(), postBody));
    }
}
