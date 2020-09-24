package io.talken.dex.api.controller;

/**
 * API Endpoint mappings
 */
public class RequestMappings {
	private static final String ROOT = "";

	private static final String DEX = ROOT + "/dex";

	// WalletService
	private static final String PRIVATE_WALLET = DEX + "/pw";
	public static final String PRIVATE_WALLET_WITHDRAW_BASE = PRIVATE_WALLET + "/transferBase";
	public static final String PRIVATE_WALLET_ANCHOR_TASK = PRIVATE_WALLET + "/anchor";
	public static final String PRIVATE_WALLET_PREPARE_LMT_TRANSFER = PRIVATE_WALLET + "/prepareLmtTransfer";
	public static final String PRIVATE_WALLET_CHECK_LMT_TRANSFER_READY = PRIVATE_WALLET + "/checkLmtTransferReady";

	private static final String TRADE_WALLET = DEX + "/tw";
	public static final String TRADE_WALLET_TXLIST = TRADE_WALLET + "/txList";
	public static final String TRADE_WALLET_BALANCE = TRADE_WALLET + "/balance";
	public static final String TRADE_WALLET_DEANCHOR_TASK = TRADE_WALLET + "/deanchor";
	public static final String TRADE_WALLET_DEANCHOR_FEE = TRADE_WALLET_DEANCHOR_TASK + "/fee";

	// OfferService
	private static final String OFFER = DEX + "/offer";

	public static final String OFFER_DETAIL = DEX + "/offer/detail/{offerId}";

	private static final String OFFER_SELL = OFFER + "/sell";
	public static final String OFFER_SELL_FEE = OFFER_SELL + "/fee";
	public static final String OFFER_SELL_CREATE_TASK = OFFER_SELL + "/create";
	public static final String OFFER_SELL_DELETE_TASK = OFFER_SELL + "/delete";

	private static final String OFFER_BUY = OFFER + "/buy";
	public static final String OFFER_BUY_FEE = OFFER_BUY + "/fee";
	public static final String OFFER_BUY_CREATE_TASK = OFFER_BUY + "/create";
	public static final String OFFER_BUY_DELETE_TASK = OFFER_BUY + "/delete";

    // StakingService
    public static final String STAKING = DEX + "/staking";
    public static final String UNSTAKING = DEX + "/unstaking";
    public static final String STAKING_AVAILABLE = STAKING + "/available";

	// MiscService
	public static final String TXLIST = DEX + "/txList"; // dexTask TxList from txMon
	public static final String CONVERT_ASSET = DEX + "/convert";
	public static final String EXCHANGE_ASSET = DEX + "/exchange";

	// BlockChainInfoService
	private static final String BLOCK_CHAIN = DEX + "/bc";
	private static final String BLOCK_CHAIN_LUNIVERSE = BLOCK_CHAIN + "/luniverse";
	public static final String BLOCK_CHAIN_LUNIVERSE_GASPRICE = BLOCK_CHAIN_LUNIVERSE + "/gasPrice";
	public static final String BLOCK_CHAIN_LUNIVERSE_TXLIST = BLOCK_CHAIN_LUNIVERSE + "/txList";

	private static final String BLOCK_CHAIN_ETHEREUM = BLOCK_CHAIN + "/ethereum";
	public static final String BLOCK_CHAIN_ETHEREUM_GETETHBALANCE = BLOCK_CHAIN_ETHEREUM + "/getEthBalance";
	public static final String BLOCK_CHAIN_ETHEREUM_GETERC20BALANCE = BLOCK_CHAIN_ETHEREUM + "/getErc20Balance";

	// TokenMetaService
	private static final String TMS = ROOT + "/tms";
	public static final String TMS_TM_LIST = TMS + "/list";
	public static final String TMS_TM_INFO = TMS + "/info/{symbol}";
	public static final String TMS_MI_LIST = TMS + "/listManaged";
    public static final String TMS_TMC_INFO = TMS + "/totalMarketCapInfo";

	// Not Implemented Yet
	private static final String ADM = "/adm";
	public static final String ADM_RELOAD_TM = ADM + "/reloadTokenMeta";
	public static final String ADM_MI_UPDATEHOLDER = ADM + "/managedInfo/{symbol}/updateHolder";
}
