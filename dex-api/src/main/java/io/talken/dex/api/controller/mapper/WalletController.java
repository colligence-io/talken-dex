package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
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
import io.talken.dex.shared.exception.ParameterViolationException;
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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
	 * @return
	 * @throws TalkenException
	 */
	@AuthRequired
	@RequestMapping(value = RequestMappings.TRADE_WALLET_BALANCE, method = RequestMethod.GET)
	public DexResponse<TradeWalletResult> getTradeWallet() throws TalkenException {
		return DexResponse.buildResponse(walletService.getTradeWalletBalances(authInfo.getUser()));
	}

	/**
	 * build template tx envelope for TalkenWallet Mobile App
	 *
	 * @param assetCode
	 * @return
	 * @throws TalkenException
	 */
	@AuthRequired
	@RequestMapping(value = RequestMappings.PRIVATE_WALLET_WITHDRAW_BASE, method = RequestMethod.GET)
	public DexResponse<PrivateWalletTransferDTO> withdraw_base(@RequestParam("assetCode") String assetCode) throws TalkenException {
		return DexResponse.buildResponse(pwService.createTransferDTObase(PrivateWalletMsgTypeEnum.TRANSFER, assetCode));
	}

	/**
	 * anchor request
	 *
	 * @param postBody
	 * @return
	 * @throws TalkenException
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
	 * @param postBody
	 * @return
	 * @throws TalkenException
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
	 * @param postBody
	 * @return
	 * @throws TalkenException
	 */
	@RequestMapping(value = RequestMappings.TRADE_WALLET_DEANCHOR_FEE, method = RequestMethod.POST)
	public DexResponse<CalculateFeeResult> deanchorFee(@RequestBody DeanchorRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(feeService.calculateDeanchorFee(postBody.getAssetCode(), postBody.getAmount()));
	}

	/**
	 * search tradewallet tx list
	 *
	 * @param address
	 * @param operationType
	 * @param assetCode
	 * @param assetIssuer
	 * @param include
	 * @param sort
	 * @param page
	 * @param offset
	 * @return
	 * @throws TalkenException
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
	 * @return
	 * @throws TalkenException
	 */
	@AuthRequired
	@RequestMapping(value = RequestMappings.PRIVATE_WALLET_PREPARE_LMT_TRANSFER, method = RequestMethod.GET)
	public DexResponse<Boolean> prepareLmtTransfer() throws TalkenException {
		return DexResponse.buildResponse(walletService.prepareTransferLuk(authInfo.getUser()));
	}

	/**
	 * check user private luniverse wallet is ready to transfer asset
	 *
	 * @param address
	 * @return
	 */
	@AuthRequired
	@RequestMapping(value = RequestMappings.PRIVATE_WALLET_CHECK_LMT_TRANSFER_READY, method = RequestMethod.GET)
	public DexResponse<Boolean> checkLmtTransferReady(@RequestParam("address") String address) {
		return DexResponse.buildResponse(walletService.checkTransferLukPrepared(address));
	}

    @Deprecated
    @AuthRequired
    @RequestMapping(value = RequestMappings.PRIVATE_WALLET_TALK_LMT_ANCHOR, method = RequestMethod.POST)
    public DexResponse<PrivateWalletTransferDTO> anchorOnlyTALKLMT(@RequestBody AnchorRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(anchorService.anchorOnlyTALKLMT(authInfo.getUser(), postBody));
    }
}
