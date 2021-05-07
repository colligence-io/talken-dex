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
    public static final String TRADE_WALLET_RECLAIM = TRADE_WALLET + "/reclaim";
    public static final String TRADE_WALLET_CLAIM = TRADE_WALLET + "/claim";

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
    public static final String STAKING_LIST = STAKING + "/list";
    public static final String STAKING_CODE = STAKING + "/code/{stakingCode}";
    public static final String STAKING_DETAIL = STAKING + "/detail/{stakingId}";

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
    public static final String BLOCK_CHAIN_ETHEREUM_GETTRANSACTIONCOUNT = BLOCK_CHAIN_ETHEREUM + "/getEthTxCount";
    public static final String BLOCK_CHAIN_ETHEREUM_GETPENDING_TXLIST = BLOCK_CHAIN_ETHEREUM + "/getEthPendingTxList";
    public static final String BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_BY_HASH = BLOCK_CHAIN_ETHEREUM + "/getTransaction";
    public static final String BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_RECEIPT_BY_HASH = BLOCK_CHAIN_ETHEREUM + "/getTransactionReceipt";

    private static final String BLOCK_CHAIN_KLAYTN = BLOCK_CHAIN + "/klaytn";
    public static final String BLOCK_CHAIN_KLAYTN_GETACCOUNT = BLOCK_CHAIN_KLAYTN + "/getAccount";
    public static final String BLOCK_CHAIN_KLAYTN_GETBALANCE = BLOCK_CHAIN_KLAYTN + "/getBalance";
    public static final String BLOCK_CHAIN_KLAYTN_GETKIP7BALANCE = BLOCK_CHAIN_KLAYTN + "/getKip7Balance";
    public static final String BLOCK_CHAIN_KLAYTN_GASPRICE = BLOCK_CHAIN_KLAYTN + "/gasPrice";
    public static final String BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_BY_HASH = BLOCK_CHAIN_KLAYTN + "/getTransaction";
    public static final String BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_RECEIPT_BY_HASH = BLOCK_CHAIN_KLAYTN + "/getTransactionReceipt";
    public static final String BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_LIST = BLOCK_CHAIN_KLAYTN + "/getTransactionList";

    public static final String BLOCK_CHAIN_KLAYTN_GETKIP7INFO = BLOCK_CHAIN_KLAYTN + "/getContract";

    private static final String BLOCK_CHAIN_BSC = BLOCK_CHAIN + "/bsc";
	public static final String BLOCK_CHAIN_BSC_GETBALANCE = BLOCK_CHAIN_BSC + "/getBalance";
	public static final String BLOCK_CHAIN_BSC_GETBEP20BALANCE = BLOCK_CHAIN_BSC + "/getBep20Balance";
	public static final String BLOCK_CHAIN_BSC_GASPRICE = BLOCK_CHAIN_BSC + "/gasPrice";
	public static final String BLOCK_CHAIN_BSC_BEP20GASPRICE = BLOCK_CHAIN_BSC + "/contractGasPrice";
	public static final String BLOCK_CHAIN_BSC_GET_TRANSACTION_BY_HASH = BLOCK_CHAIN_BSC + "/getTransaction";
	public static final String BLOCK_CHAIN_BSC_GET_TRANSACTION_RECEIPT_BY_HASH = BLOCK_CHAIN_BSC + "/getTransactionReceipt";

	private static final String BLOCK_CHAIN_HECO = BLOCK_CHAIN + "/heco";
	public static final String BLOCK_CHAIN_HECO_GETBALANCE = BLOCK_CHAIN_HECO + "/getBalance";
	public static final String BLOCK_CHAIN_HECO_GETHRC20BALANCE = BLOCK_CHAIN_HECO + "/getHrc20Balance";
	public static final String BLOCK_CHAIN_HECO_GASPRICE = BLOCK_CHAIN_HECO + "/gasPrice";
	public static final String BLOCK_CHAIN_HECO_HRC20GASPRICE = BLOCK_CHAIN_HECO + "/contractGasPrice";
	public static final String BLOCK_CHAIN_HECO_GET_TRANSACTION_BY_HASH = BLOCK_CHAIN_HECO + "/getTransaction";
	public static final String BLOCK_CHAIN_HECO_GET_TRANSACTION_RECEIPT_BY_HASH = BLOCK_CHAIN_HECO + "/getTransactionReceipt";

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

	// deprecated
    public static final String PRIVATE_WALLET_TALK_LMT_ANCHOR = PRIVATE_WALLET + "/talkLMTAnchor";
}
