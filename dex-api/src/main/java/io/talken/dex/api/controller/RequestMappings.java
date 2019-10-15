package io.talken.dex.api.controller;

public class RequestMappings {
	private static final String ROOT = "";

	private static final String DEX = ROOT + "/dex";

	private static final String DEXKEY_SUFFIX = "/dexKey";

	// WalletService
	private static final String PRIVATE_WALLET = DEX + "/pw";
	public static final String PRIVATE_WALLET_WITHDRAW_BASE = PRIVATE_WALLET + "/withdraw_base";
	public static final String PRIVATE_WALLET_ANCHOR_TASK = PRIVATE_WALLET + "/anchor";

	private static final String TRADE_WALLET = DEX + "/tw";
	public static final String TRADE_WALLET_BALANCE = TRADE_WALLET + "/balance";
	public static final String TRADE_WALLET_DEANCHOR_TASK = TRADE_WALLET + "/deanchor";
	public static final String TRADE_WALLET_DEANCHOR_FEE = TRADE_WALLET_DEANCHOR_TASK + "/fee";

	// OfferService
	private static final String OFFER = DEX + "/offer";

	public static final String OFFER_SELL = OFFER + "/sell";
	public static final String OFFER_SELL_FEE = OFFER_SELL + "/fee";
	public static final String OFFER_SELL_CREATE_TASK = OFFER_SELL + "/create";
	public static final String OFFER_SELL_CREATE_TASK_DEXKEY = OFFER_SELL_CREATE_TASK + DEXKEY_SUFFIX;
	public static final String OFFER_SELL_DELETE_TASK = OFFER_SELL + "/delete";
	public static final String OFFER_SELL_DELETE_TASK_DEXKEY = OFFER_SELL_DELETE_TASK + DEXKEY_SUFFIX;

	public static final String OFFER_BUY = OFFER + "/buy";
	public static final String OFFER_BUY_FEE = OFFER_BUY + "/fee";
	public static final String OFFER_BUY_CREATE_TASK = OFFER_BUY + "/create";
	public static final String OFFER_BUY_CREATE_TASK_DEXKEY = OFFER_BUY_CREATE_TASK + DEXKEY_SUFFIX;
	public static final String OFFER_BUY_DELETE_TASK = OFFER_BUY + "/delete";
	public static final String OFFER_BUY_DELETE_TASK_DEXKEY = OFFER_BUY_DELETE_TASK + DEXKEY_SUFFIX;

	// Swap Service
	private static final String SWAP = DEX + "/swap";
	public static final String SWAP_PREDICT = SWAP + "/predict";
	public static final String SWAP_TASK = SWAP + "/swap";
	public static final String SWAP_TASK_DEXKEY = SWAP_TASK + DEXKEY_SUFFIX;

	// MiscService
	public static final String TXLIST = DEX + "/txList";
	public static final String CONVERT_ASSET = DEX + "/convert";
	public static final String EXCHANGE_ASSET = DEX + "/exchange";

	// BlockChainInfoService
	private static final String BLOCK_CHAIN = DEX + "/bc";
	public static final String BLOCK_CHAIN_TRANSFER_BCINFO = BLOCK_CHAIN + "/transferBase";
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

	// Not Implemented Yet
	private static final String ADM = "/adm";
	public static final String ADM_RELOAD_TM = ADM + "/reloadTokenMeta";
	public static final String ADM_MI_UPDATEHOLDER = ADM + "/managedInfo/{symbol}/updateHolder";
}
