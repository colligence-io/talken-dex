package io.colligence.talken.dex.api;

public class RequestMappings {
	private static final String ROOT = "";

	private static final String DEX = ROOT + "/dex";

//	public static final String SUBMIT_SUFFIX = "_submit";

	private static final String ANCHOR = DEX + "/anchor";
	public static final String ANCHOR_TASK = ANCHOR + "/anchor";
	public static final String DEANCHOR_TASK = ANCHOR + "/deanchor";
	public static final String DEANCHOR_DEXKEY = ANCHOR + "/deanchor/dexKey";


	private static final String OFFER = DEX + "/offer";
	public static final String CREATE_OFFER = OFFER + "/createOffer";
	//	public static final String CREATE_PASSIVE_OFFER = OFFER + "/createPassiveOffer";
	public static final String DELETE_OFFER = OFFER + "/deleteOffer";

	private static final String MAS = ROOT + "/mas";

	private static final String MA = MAS + "/ma";
	public static final String MA_LIST = MA + "/list";
	public static final String MA_RELOAD = MA + "/reload";
	public static final String MA_UPDATEHOLDER = MA + "/updateHolder";

	private static final String SIGNTASK = MAS + "/signTask";
	public static final String SIGNTASK_ADD = SIGNTASK + "/add";
	public static final String SIGNTASK_LIST = SIGNTASK + "/list";
	public static final String SIGNTASK_UPDATE = SIGNTASK + "/update";
}
