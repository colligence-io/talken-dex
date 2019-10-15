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
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import io.talken.dex.api.service.integration.PrivateWalletMsgTypeEnum;
import io.talken.dex.api.service.integration.PrivateWalletService;
import io.talken.dex.api.service.integration.PrivateWalletTransferDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


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

	@AuthRequired
	@RequestMapping(value = RequestMappings.TRADE_WALLET_BALANCE, method = RequestMethod.GET)
	public DexResponse<TradeWalletResult> ensureTradeWallet() throws TalkenException {
		return DexResponse.buildResponse(walletService.getTradeWalletBalances(authInfo.getUser()));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.PRIVATE_WALLET_WITHDRAW_BASE, method = RequestMethod.GET)
	public DexResponse<PrivateWalletTransferDTO> withdraw_base(@RequestParam("assetCode") String assetCode) throws TalkenException {
		return DexResponse.buildResponse(pwService.createTransferDTObase(PrivateWalletMsgTypeEnum.WITHDRAW, assetCode));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.PRIVATE_WALLET_ANCHOR_TASK, method = RequestMethod.POST)
	public DexResponse<PrivateWalletTransferDTO> anchor(@RequestBody AnchorRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(anchorService.anchor(authInfo.getUser(), postBody));
	}
//
//	@AuthRequired
//	@RequestMapping(value = RequestMappings.TRADE_WALLET_DEANCHOR_TASK, method = RequestMethod.POST)
//	public DexResponse<DeanchorResult> deanchor(@RequestBody DeanchorRequest postBody) throws TalkenException {
//		DTOValidator.validate(postBody);
//		return DexResponse.buildResponse(anchorService.deanchor(authInfo.getUser(), postBody));
//	}

	@RequestMapping(value = RequestMappings.TRADE_WALLET_DEANCHOR_FEE, method = RequestMethod.POST)
	public DexResponse<CalculateFeeResult> deanchorFee(@RequestBody DeanchorRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(feeService.calculateDeanchorFee(postBody.getAssetCode(), postBody.getAmount(), postBody.getFeeByTalk()));
	}
}
