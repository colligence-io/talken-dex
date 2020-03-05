package io.talken.dex.governance.service.mam;


import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.GovSettings;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.service.blockchain.ethereum.Erc20ContractInfoService;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;
import org.stellar.sdk.requests.AssetsRequestBuilder;
import org.stellar.sdk.requests.ErrorResponse;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.AssetResponse;
import org.stellar.sdk.responses.Page;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static io.talken.common.persistence.jooq.Tables.USER_REWARD;

/**
 * Monitor Managed Account status
 * NOTE this service is very-hard-coded
 */
@Service
@Scope("singleton")
public class MaMonitorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(MaMonitorService.class);

	@Autowired
	private EthereumNetworkService ethereumNetworkService;

	@Autowired
	private LuniverseNetworkService luniverseNetworkService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private TokenMetaGovService tmService;

	@Autowired
	private GovSettings govSettings;

	@Autowired
	private AdminAlarmService adminAlarmService;

	@Autowired
	private Erc20ContractInfoService contractInfoService;

	@Autowired
	private DSLContext dslContext;

	private boolean isSuspended = false;

	public void suspend() {
		logger.info("MaMonitorService SUSPENDED by admin.");
		isSuspended = true;
	}

	public void resume() {
		logger.info("MaMonitorService RESUMED by admin.");
		isSuspended = false;
	}

	// check every one hour
	@Scheduled(fixedDelay = 1000 * 60 * 60, initialDelay = 1000)
	private void checkAll() {
		if(isSuspended) return;
		if(DexGovStatus.isStopped) return;

		try {
			checkTradeWalletMaster();
		} catch(ErrorResponse error) {
			adminAlarmService.error(logger, "Stellar Error {} : {}", error.getCode(), error.getBody());
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex);
		}
		try {
			checkChannel();
		} catch(ErrorResponse error) {
			adminAlarmService.error(logger, "Stellar Error {} : {}", error.getCode(), error.getBody());
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex);
		}
		try {
			checkManagedAccounts();
		} catch(ErrorResponse error) {
			adminAlarmService.error(logger, "Stellar Error {} : {}", error.getCode(), error.getBody());
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex);
		}
	}

	private void checkTradeWalletMaster() throws Exception {
		AccountResponse ar = stellarNetworkService.pickServer().accounts().account(govSettings.getTradeWallet().getCreatorAddress());

		BigDecimal bal = StellarConverter.getAccountNativeBalance(ar);

		if(bal == null) {
			adminAlarmService.error(logger, "Cannot get TradeWalletMaster native balance : ", govSettings.getTradeWallet().getCreatorAddress());
		} else if(bal.compareTo(govSettings.getMam().getCreatorMinBalance()) <= 0) {
			adminAlarmService.warn(logger, "TradeWalletMaster balance is below minumum : {}(<{}) XLM", bal.stripTrailingZeros().toPlainString(), govSettings.getMam().getCreatorMinBalance());
		}
	}

	private void checkChannel() throws Exception {
		for(String chan : stellarNetworkService.getChannelList()) {
			AccountResponse ar = stellarNetworkService.pickServer().accounts().account(chan);

			BigDecimal bal = StellarConverter.getAccountNativeBalance(ar);

			if(bal == null) {
				adminAlarmService.error(logger, "Cannot get channel native balance : {}", chan);
			} else if(bal.compareTo(govSettings.getMam().getChannelMinBalance()) <= 0) {
				adminAlarmService.warn(logger, "Stellar channel {} balance is below minumum : {}(<{}) XLM", chan, bal.stripTrailingZeros().toPlainString(), govSettings.getMam().getChannelMinBalance());
			}
		}
	}

	private void checkManagedAccounts() throws Exception {
		// issuer addresses
		Set<String> issuers = new HashSet<>();
		// dexAssetType, balance
		Map<Asset, BigDecimal> supplies = new HashMap<>();

		// accountId, accountResponse
		Map<String, AccountResponse> ars = new HashMap<>();

		// select unsent rewards
		Map<String, BigDecimal> unsentPrivateRewards = dslContext.select(USER_REWARD.ASSETCODE, DSL.sum(USER_REWARD.AMOUNT))
				.from(USER_REWARD)
				.where(
						USER_REWARD.APPROVEMENT_FLAG.eq(true)
								.and(USER_REWARD.PRIVATE_WALLET_FLAG.eq(true))
								.and(USER_REWARD.BCTX_ID.isNull())
				)
				.groupBy(USER_REWARD.ASSETCODE)
				.fetch()
				.stream()
				.collect(Collectors.toMap(Record2::value1, Record2::value2));

		// get ma balances
		for(Map.Entry<String, TokenMetaTable.Meta> _kv : tmService.getTokenMetaManagedList().entrySet()) {
			String assetCode = _kv.getKey();
			TokenMetaTable.Meta meta = _kv.getValue();

			if(!issuers.contains(meta.getManagedInfo().getIssuerAddress())) {
				Page<AssetResponse> assetResponsePage = ((AssetsRequestBuilder) (stellarNetworkService.pickServer().assets().assetIssuer(meta.getManagedInfo().getIssuerAddress()).limit(200))).execute();
				for(AssetResponse assetResponse : assetResponsePage.getRecords()) {
					supplies.put(assetResponse.getAsset(), StellarConverter.scale(new BigDecimal(assetResponse.getAmount())));
				}
				issuers.add(meta.getManagedInfo().getIssuerAddress());
			}

			// cache stellar account responses
			addStellarAccountResponse(ars, meta.getManagedInfo().getIssuerAddress());
			addStellarAccountResponse(ars, meta.getManagedInfo().getOfferFeeHolderAddress());
			addStellarAccountResponse(ars, meta.getManagedInfo().getDeancFeeHolderAddress());
			addStellarAccountResponse(ars, meta.getManagedInfo().getSwapFeeHolderAddress());

			BigDecimal feeTotal = getStellarBalance(ars, meta.getManagedInfo().getOfferFeeHolderAddress(), meta.getManagedInfo().dexAssetType())
					.add(getStellarBalance(ars, meta.getManagedInfo().getDeancFeeHolderAddress(), meta.getManagedInfo().dexAssetType()))
					.add(getStellarBalance(ars, meta.getManagedInfo().getSwapFeeHolderAddress(), meta.getManagedInfo().dexAssetType()));


			// get holder balances
			BigDecimal holderTotal = BigDecimal.ZERO;
			StringJoiner holderAddresses = new StringJoiner(",");
			for(TokenMetaTable.HolderAccountInfo assetHolderAccount : meta.getManagedInfo().getAssetHolderAccounts()) {
				ObjectPair<BigDecimal, BigDecimal> hBal = getAccountBalance(meta, assetHolderAccount.getAddress());
				holderTotal = holderTotal.add(hBal.second());
				holderAddresses.add(assetHolderAccount.getAddress());

				// check holder native balance
				ObjectPair<BigDecimal, String> minBal = getNetfeeBuffer(meta, "holder");
				if(hBal.second().compareTo(BigDecimal.ZERO) > 0 && hBal.first().compareTo(minBal.first()) < 0) {
					adminAlarmService.warn(logger, "MAM : {} Holder( {} ) low native balance for network fee : {} < {} {}", meta.getSymbol(), assetHolderAccount.getAddress(), hBal.first().stripTrailingZeros().toPlainString(), minBal.first().stripTrailingZeros().toPlainString(), minBal.second());
				}
			}

			// compare holderTotal <-> supply - feeTotal
			BigDecimal supply = supplies.get(meta.getManagedInfo().dexAssetType());
			if(supply != null) {
				// if holder has low balance than effective supply (supply - feeTotal)
				if(holderTotal.compareTo(supply.subtract(feeTotal)) < 0) {
					adminAlarmService.warn(logger, "MAM : {} Holder( {} ) lower balance than effective supply ({} issued - {} fee) : {} < {} {}", assetCode, holderAddresses.toString(), supply.stripTrailingZeros().toPlainString(), feeTotal.stripTrailingZeros().toPlainString(), holderTotal.stripTrailingZeros().toPlainString(), supply.subtract(feeTotal).stripTrailingZeros().toPlainString(), assetCode);
				}
			} else {
				// XXX : never issued? or just error?
				//adminAlarmService.error(logger, "Cannot find supply for {}", assetCode);
			}


			if(meta.getManagedInfo().getDistributorAddress() != null) {
				BigDecimal unsent = unsentPrivateRewards.get(meta.getSymbol());
				if(unsent != null) {
					ObjectPair<BigDecimal, BigDecimal> accountBalance = getAccountBalance(meta, meta.getManagedInfo().getDistributorAddress());
					ObjectPair<BigDecimal, String> netfeeBuffer = getNetfeeBuffer(meta, "distributor");

					if(meta.getNativeFlag()) {
						BigDecimal effBal = accountBalance.first().subtract(netfeeBuffer.first());
						if(effBal.compareTo(unsent) < 0) {
							adminAlarmService.warn(logger, "MAM : {} Distributor( {} ) lower balance than unsent rewards : {} < {} (+{}) {}", assetCode, meta.getManagedInfo().getDistributorAddress(), accountBalance.first().stripTrailingZeros().toPlainString(), unsent.stripTrailingZeros().toPlainString(), netfeeBuffer.first().stripTrailingZeros().toPlainString(), netfeeBuffer.second());
						}
					} else {
						if(accountBalance.first().compareTo(netfeeBuffer.first()) < 0) {
							adminAlarmService.warn(logger, "MAN : {} Distributor( {} ) low native balance for network fee : {} < {} {}", assetCode, meta.getManagedInfo().getDistributorAddress(), accountBalance.first().stripTrailingZeros().toPlainString(), netfeeBuffer.first().stripTrailingZeros().toPlainString(), netfeeBuffer.second());
						}
						if(unsent.compareTo(accountBalance.second()) > 0) {
							adminAlarmService.warn(logger, "MAM : {} Distributor( {} ) lower balance than unsent rewards : {} < {} {}", assetCode, meta.getManagedInfo().getDistributorAddress(), accountBalance.second().stripTrailingZeros().toPlainString(), unsent.stripTrailingZeros().toPlainString(), assetCode);
						}
					}
				}


			}
		}

		// check issuer XLM balance
		for(String issuer : issuers) {
			if(!ars.containsKey(issuer)) {
				adminAlarmService.error(logger, "Cannot find issuer account information : {}", issuer);
			} else {
				BigDecimal issuerBal = StellarConverter.getAccountNativeBalance(ars.get(issuer));
				if(issuerBal.compareTo(govSettings.getMam().getIssuerMinBalance()) <= 0) {
					adminAlarmService.warn(logger, "MAM : Issuer( {} ) native balance is below minumum : {}(<{}) XLM ", issuer, issuerBal.stripTrailingZeros().toPlainString(), govSettings.getMam().getIssuerMinBalance());
				}
			}
		}
	}


	private ObjectPair<BigDecimal, BigDecimal> getAccountBalance(TokenMetaTable.Meta meta, String address) throws Exception {
		BigDecimal coin = null;
		BigDecimal token = null;
		switch(meta.getBctxType()) {
			case STELLAR_TOKEN:
				token = StellarConverter.getAccountBalance(stellarNetworkService.pickServer().accounts().account(address), Asset.createNonNativeAsset(meta.getSymbol(), meta.getAux().get(TokenMetaAuxCodeEnum.STELLAR_ISSUER_ID).toString()));
			case STELLAR:
				coin = StellarConverter.getAccountNativeBalance(stellarNetworkService.pickServer().accounts().account(address));
				break;
			case ETHEREUM_ERC20_TOKEN:
				BigInteger rawErc20Bal = contractInfoService.getBalanceOf(ethereumNetworkService.getInfuraClient().newClient(), meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString(), address);
				if(rawErc20Bal != null)
					token = new BigDecimal(rawErc20Bal).divide(BigDecimal.TEN.pow(meta.getUnitDecimals() != null ? meta.getUnitDecimals() : 18), RoundingMode.FLOOR);
			case ETHEREUM:
				BigInteger rawEthBal = ethereumNetworkService.getInfuraClient().newClient().ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
				if(rawEthBal != null)
					coin = Convert.fromWei(rawEthBal.toString(), Convert.Unit.ETHER);
				break;
			case LUNIVERSE_MAIN_TOKEN:
				BigInteger rawLmtBal = contractInfoService.getBalanceOf(luniverseNetworkService.newMainRpcClient(), meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString(), address);
				if(rawLmtBal != null)
					token = new BigDecimal(rawLmtBal).divide(BigDecimal.TEN.pow(meta.getUnitDecimals() != null ? meta.getUnitDecimals() : 18), RoundingMode.FLOOR);
			case LUNIVERSE:
				BigInteger rawLukBal = luniverseNetworkService.newMainRpcClient().ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
				if(rawLukBal != null)
					coin = Convert.fromWei(rawLukBal.toString(), Convert.Unit.ETHER);
				break;
			case BITCOIN:
				break;
		}

		if(coin == null) coin = BigDecimal.ZERO;
		if(token == null) token = BigDecimal.ZERO;

		return new ObjectPair<>(coin, token);
	}

	private ObjectPair<BigDecimal, String> getNetfeeBuffer(TokenMetaTable.Meta meta, String type) {
		BigDecimal rtn = null;
		String coinSymbol = null;
		switch(meta.getBctxType()) {
			case STELLAR_TOKEN:
			case STELLAR:
				coinSymbol = "XLM";
				break;
			case ETHEREUM_ERC20_TOKEN:
			case ETHEREUM:
				coinSymbol = "ETH";
				break;
			case LUNIVERSE_MAIN_TOKEN:
			case LUNIVERSE:
				coinSymbol = "LUK";
				break;
			case BITCOIN:
				coinSymbol = "BTC";
				break;
		}

		if(type.equals("holder")) {
			rtn = govSettings.getMam().getNetfeeBuffer().getHolder().get(coinSymbol);
		} else {
			rtn = govSettings.getMam().getNetfeeBuffer().getDistributor().get(coinSymbol);
		}

		return new ObjectPair<>(rtn == null ? BigDecimal.ZERO : rtn, coinSymbol);
	}


	private void addStellarAccountResponse(Map<String, AccountResponse> ars, String accountId) throws Exception {
		if(!ars.containsKey(accountId)) {
			AccountResponse ar = stellarNetworkService.pickServer().accounts().account(accountId);
			ars.put(accountId, ar);
		}
	}

	private BigDecimal getStellarBalance(Map<String, AccountResponse> ars, String accountId, Asset asset) {
		if(!ars.containsKey(accountId)) return BigDecimal.ZERO;
		return StellarConverter.getAccountBalance(ars.get(accountId), asset);
	}
}
