package io.talken.dex.api.controller;

/**
 * API Endpoint mappings
 */
public class RequestMappings {
	private static final String ROOT = "";

	private static final String DEX = ROOT + "/dex";

	// WalletService
	private static final String PRIVATE_WALLET = DEX + "/pw";
    /**
     * The constant PRIVATE_WALLET_WITHDRAW_BASE.
     */
    public static final String PRIVATE_WALLET_WITHDRAW_BASE = PRIVATE_WALLET + "/transferBase";
    /**
     * The constant PRIVATE_WALLET_ANCHOR_TASK.
     */
    public static final String PRIVATE_WALLET_ANCHOR_TASK = PRIVATE_WALLET + "/anchor";
    /**
     * The constant PRIVATE_WALLET_PREPARE_LMT_TRANSFER.
     */
    public static final String PRIVATE_WALLET_PREPARE_LMT_TRANSFER = PRIVATE_WALLET + "/prepareLmtTransfer";
    /**
     * The constant PRIVATE_WALLET_CHECK_LMT_TRANSFER_READY.
     */
    public static final String PRIVATE_WALLET_CHECK_LMT_TRANSFER_READY = PRIVATE_WALLET + "/checkLmtTransferReady";

	private static final String TRADE_WALLET = DEX + "/tw";
    /**
     * The constant TRADE_WALLET_TXLIST.
     */
    public static final String TRADE_WALLET_TXLIST = TRADE_WALLET + "/txList";
    /**
     * The constant TRADE_WALLET_BALANCE.
     */
    public static final String TRADE_WALLET_BALANCE = TRADE_WALLET + "/balance";
    /**
     * The constant TRADE_WALLET_DEANCHOR_TASK.
     */
    public static final String TRADE_WALLET_DEANCHOR_TASK = TRADE_WALLET + "/deanchor";
    /**
     * The constant TRADE_WALLET_DEANCHOR_FEE.
     */
    public static final String TRADE_WALLET_DEANCHOR_FEE = TRADE_WALLET_DEANCHOR_TASK + "/fee";
    /**
     * The constant TRADE_WALLET_RECLAIM.
     */
    public static final String TRADE_WALLET_RECLAIM = TRADE_WALLET + "/reclaim";
    /**
     * The constant TRADE_WALLET_CLAIM.
     */
    public static final String TRADE_WALLET_CLAIM = TRADE_WALLET + "/claim";

	// OfferService
	private static final String OFFER = DEX + "/offer";

    /**
     * The constant OFFER_DETAIL.
     */
    public static final String OFFER_DETAIL = DEX + "/offer/detail/{offerId}";

	private static final String OFFER_SELL = OFFER + "/sell";
    /**
     * The constant OFFER_SELL_FEE.
     */
    public static final String OFFER_SELL_FEE = OFFER_SELL + "/fee";
    /**
     * The constant OFFER_SELL_CREATE_TASK.
     */
    public static final String OFFER_SELL_CREATE_TASK = OFFER_SELL + "/create";
    /**
     * The constant OFFER_SELL_DELETE_TASK.
     */
    public static final String OFFER_SELL_DELETE_TASK = OFFER_SELL + "/delete";

	private static final String OFFER_BUY = OFFER + "/buy";
    /**
     * The constant OFFER_BUY_FEE.
     */
    public static final String OFFER_BUY_FEE = OFFER_BUY + "/fee";
    /**
     * The constant OFFER_BUY_CREATE_TASK.
     */
    public static final String OFFER_BUY_CREATE_TASK = OFFER_BUY + "/create";
    /**
     * The constant OFFER_BUY_DELETE_TASK.
     */
    public static final String OFFER_BUY_DELETE_TASK = OFFER_BUY + "/delete";

    /**
     * The constant STAKING.
     */
// StakingService
    public static final String STAKING = DEX + "/staking";
    /**
     * The constant UNSTAKING.
     */
    public static final String UNSTAKING = DEX + "/unstaking";
    /**
     * The constant STAKING_AVAILABLE.
     */
    public static final String STAKING_AVAILABLE = STAKING + "/available";
    /**
     * The constant STAKING_LIST.
     */
    public static final String STAKING_LIST = STAKING + "/list";
    /**
     * The constant STAKING_CODE.
     */
    public static final String STAKING_CODE = STAKING + "/code/{stakingCode}";
    /**
     * The constant STAKING_DETAIL.
     */
    public static final String STAKING_DETAIL = STAKING + "/detail/{stakingId}";

    /**
     * The constant TXLIST.
     */
// MiscService
	public static final String TXLIST = DEX + "/txList"; // dexTask TxList from txMon
    /**
     * The constant CONVERT_ASSET.
     */
    public static final String CONVERT_ASSET = DEX + "/convert";
    /**
     * The constant EXCHANGE_ASSET.
     */
    public static final String EXCHANGE_ASSET = DEX + "/exchange";

	// BlockChainInfoService
	private static final String BLOCK_CHAIN = DEX + "/bc";
	private static final String BLOCK_CHAIN_LUNIVERSE = BLOCK_CHAIN + "/luniverse";
    /**
     * The constant BLOCK_CHAIN_LUNIVERSE_GASPRICE.
     */
    public static final String BLOCK_CHAIN_LUNIVERSE_GASPRICE = BLOCK_CHAIN_LUNIVERSE + "/gasPrice";
    /**
     * The constant BLOCK_CHAIN_LUNIVERSE_TXLIST.
     */
    public static final String BLOCK_CHAIN_LUNIVERSE_TXLIST = BLOCK_CHAIN_LUNIVERSE + "/txList";

	private static final String BLOCK_CHAIN_ETHEREUM = BLOCK_CHAIN + "/ethereum";
    /**
     * The constant BLOCK_CHAIN_ETHEREUM_GETETHBALANCE.
     */
    public static final String BLOCK_CHAIN_ETHEREUM_GETETHBALANCE = BLOCK_CHAIN_ETHEREUM + "/getEthBalance";
    /**
     * The constant BLOCK_CHAIN_ETHEREUM_GETERC20BALANCE.
     */
    public static final String BLOCK_CHAIN_ETHEREUM_GETERC20BALANCE = BLOCK_CHAIN_ETHEREUM + "/getErc20Balance";
    /**
     * The constant BLOCK_CHAIN_ETHEREUM_GETTRANSACTIONCOUNT.
     */
    public static final String BLOCK_CHAIN_ETHEREUM_GETTRANSACTIONCOUNT = BLOCK_CHAIN_ETHEREUM + "/getEthTxCount";
    /**
     * The constant BLOCK_CHAIN_ETHEREUM_GETPENDING_TXLIST.
     */
    public static final String BLOCK_CHAIN_ETHEREUM_GETPENDING_TXLIST = BLOCK_CHAIN_ETHEREUM + "/getEthPendingTxList";
    /**
     * The constant BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_BY_HASH.
     */
    public static final String BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_BY_HASH = BLOCK_CHAIN_ETHEREUM + "/getTransaction";
    /**
     * The constant BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_RECEIPT_BY_HASH.
     */
    public static final String BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_RECEIPT_BY_HASH = BLOCK_CHAIN_ETHEREUM + "/getTransactionReceipt";

    private static final String BLOCK_CHAIN_KLAYTN = BLOCK_CHAIN + "/klaytn";
    /**
     * The constant BLOCK_CHAIN_KLAYTN_GETACCOUNT.
     */
    public static final String BLOCK_CHAIN_KLAYTN_GETACCOUNT = BLOCK_CHAIN_KLAYTN + "/getAccount";
    /**
     * The constant BLOCK_CHAIN_KLAYTN_GETBALANCE.
     */
    public static final String BLOCK_CHAIN_KLAYTN_GETBALANCE = BLOCK_CHAIN_KLAYTN + "/getBalance";
    /**
     * The constant BLOCK_CHAIN_KLAYTN_GETKIP7BALANCE.
     */
    public static final String BLOCK_CHAIN_KLAYTN_GETKIP7BALANCE = BLOCK_CHAIN_KLAYTN + "/getKip7Balance";
    /**
     * The constant BLOCK_CHAIN_KLAYTN_GASPRICE.
     */
    public static final String BLOCK_CHAIN_KLAYTN_GASPRICE = BLOCK_CHAIN_KLAYTN + "/gasPrice";
    /**
     * The constant BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_BY_HASH.
     */
    public static final String BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_BY_HASH = BLOCK_CHAIN_KLAYTN + "/getTransaction";
    /**
     * The constant BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_RECEIPT_BY_HASH.
     */
    public static final String BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_RECEIPT_BY_HASH = BLOCK_CHAIN_KLAYTN + "/getTransactionReceipt";
    /**
     * The constant BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_LIST.
     */
    public static final String BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_LIST = BLOCK_CHAIN_KLAYTN + "/getTransactionList";

    /**
     * The constant BLOCK_CHAIN_KLAYTN_GETKIP7INFO.
     */
    public static final String BLOCK_CHAIN_KLAYTN_GETKIP7INFO = BLOCK_CHAIN_KLAYTN + "/getContract";

    private static final String BLOCK_CHAIN_BSC = BLOCK_CHAIN + "/bsc";
    /**
     * The constant BLOCK_CHAIN_BSC_GETBALANCE.
     */
    public static final String BLOCK_CHAIN_BSC_GETBALANCE = BLOCK_CHAIN_BSC + "/getBalance";
    /**
     * The constant BLOCK_CHAIN_BSC_GETBEP20BALANCE.
     */
    public static final String BLOCK_CHAIN_BSC_GETBEP20BALANCE = BLOCK_CHAIN_BSC + "/getBep20Balance";
    /**
     * The constant BLOCK_CHAIN_BSC_GASPRICE.
     */
    public static final String BLOCK_CHAIN_BSC_GASPRICE = BLOCK_CHAIN_BSC + "/gasPrice";
    /**
     * The constant BLOCK_CHAIN_BSC_BEP20GASPRICE.
     */
    public static final String BLOCK_CHAIN_BSC_BEP20GASPRICE = BLOCK_CHAIN_BSC + "/contractGasPrice";
    /**
     * The constant BLOCK_CHAIN_BSC_GET_TRANSACTION_BY_HASH.
     */
    public static final String BLOCK_CHAIN_BSC_GET_TRANSACTION_BY_HASH = BLOCK_CHAIN_BSC + "/getTransaction";
    /**
     * The constant BLOCK_CHAIN_BSC_GET_TRANSACTION_RECEIPT_BY_HASH.
     */
    public static final String BLOCK_CHAIN_BSC_GET_TRANSACTION_RECEIPT_BY_HASH = BLOCK_CHAIN_BSC + "/getTransactionReceipt";

	private static final String BLOCK_CHAIN_HECO = BLOCK_CHAIN + "/heco";
    /**
     * The constant BLOCK_CHAIN_HECO_GETBALANCE.
     */
    public static final String BLOCK_CHAIN_HECO_GETBALANCE = BLOCK_CHAIN_HECO + "/getBalance";
    /**
     * The constant BLOCK_CHAIN_HECO_GETHRC20BALANCE.
     */
    public static final String BLOCK_CHAIN_HECO_GETHRC20BALANCE = BLOCK_CHAIN_HECO + "/getHrc20Balance";
    /**
     * The constant BLOCK_CHAIN_HECO_GASPRICE.
     */
    public static final String BLOCK_CHAIN_HECO_GASPRICE = BLOCK_CHAIN_HECO + "/gasPrice";
    /**
     * The constant BLOCK_CHAIN_HECO_HRC20GASPRICE.
     */
    public static final String BLOCK_CHAIN_HECO_HRC20GASPRICE = BLOCK_CHAIN_HECO + "/contractGasPrice";
    /**
     * The constant BLOCK_CHAIN_HECO_GET_TRANSACTION_BY_HASH.
     */
    public static final String BLOCK_CHAIN_HECO_GET_TRANSACTION_BY_HASH = BLOCK_CHAIN_HECO + "/getTransaction";
    /**
     * The constant BLOCK_CHAIN_HECO_GET_TRANSACTION_RECEIPT_BY_HASH.
     */
    public static final String BLOCK_CHAIN_HECO_GET_TRANSACTION_RECEIPT_BY_HASH = BLOCK_CHAIN_HECO + "/getTransactionReceipt";

    // TokenMetaService
	private static final String TMS = ROOT + "/tms";
    /**
     * The constant TMS_TM_LIST.
     */
    public static final String TMS_TM_LIST = TMS + "/list";
    /**
     * The constant TMS_TM_INFO.
     */
    public static final String TMS_TM_INFO = TMS + "/info/{symbol}";
    /**
     * The constant TMS_MI_LIST.
     */
    public static final String TMS_MI_LIST = TMS + "/listManaged";
    /**
     * The constant TMS_TMC_INFO.
     */
    public static final String TMS_TMC_INFO = TMS + "/totalMarketCapInfo";
    /**
     * The constant TMS_CONTRACT_ADD.
     */
    public static final String TMS_CONTRACT_ADD = TMS + "/contract";
    /**
     * The constant TMS_CONTRACT_REMOVE.
     */
    public static final String TMS_CONTRACT_REMOVE = TMS + "/contract/{contractAddress}";
    /**
     * The constant TMS_CONTRACT_LIST.
     */
    public static final String TMS_CONTRACT_LIST = TMS + "/contract/list";

	// Not Implemented Yet
	private static final String ADM = "/adm";
    /**
     * The constant ADM_RELOAD_TM.
     */
    public static final String ADM_RELOAD_TM = ADM + "/reloadTokenMeta";
    /**
     * The constant ADM_MI_UPDATEHOLDER.
     */
    public static final String ADM_MI_UPDATEHOLDER = ADM + "/managedInfo/{symbol}/updateHolder";

    /**
     * The constant PRIVATE_WALLET_TALK_LMT_ANCHOR.
     */
// deprecated
    public static final String PRIVATE_WALLET_TALK_LMT_ANCHOR = PRIVATE_WALLET + "/talkLMTAnchor";
}
