package io.colligence.talken.dex.api.controller;

public class RequestMappings {
	private static final String ROOT = "";

	private static final String DEX = ROOT + "/dex";

	private static final String DEXKEY_SUFFIX = "/dexKey";

	public static final String CONVERT_ASSET = DEX + "/convert";
	public static final String EXCHANGE_ASSET = DEX + "/exchange";

	private static final String ANCHOR = DEX + "/anchor";
	public static final String ANCHOR_TASK = ANCHOR + "/anchor";
	public static final String ANCHOR_TASK_DEXKEY = ANCHOR_TASK + DEXKEY_SUFFIX;
	public static final String DEANCHOR_TASK = ANCHOR + "/deanchor";
	public static final String DEANCHOR_TASK_DEXKEY = DEANCHOR_TASK + DEXKEY_SUFFIX;

	private static final String OFFER = DEX + "/offer";
	public static final String CREATE_OFFER = OFFER + "/createOffer";
	public static final String CREATE_OFFER_DEXKEY = CREATE_OFFER + DEXKEY_SUFFIX;
	public static final String DELETE_OFFER = OFFER + "/deleteOffer";
	public static final String DELETE_OFFER_DEXKEY = DELETE_OFFER + DEXKEY_SUFFIX;

	public static final String TXLIST = DEX + "/txList";

	private static final String TMS = ROOT + "/tms";
	public static final String TMS_TM_RELOAD = TMS + "/reload";
	public static final String TMS_TM_LIST = TMS + "/tmList";
	public static final String TMS_TM_INFO = TMS + "/tmInfo/{symbol}";
	public static final String TMS_MI_LIST = TMS + "/miList";
	public static final String TMS_MI_INFO = TMS + "/miInfo/{symbol}";
	public static final String TMS_MI_UPDATEHOLDER = TMS_MI_INFO + "/updateHolder";
}
