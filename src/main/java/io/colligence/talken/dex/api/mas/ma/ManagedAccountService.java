package io.colligence.talken.dex.api.mas.ma;

import io.colligence.talken.common.RunningProfile;
import io.colligence.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.collection.SingleKeyTable;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.api.mas.ma.dto.UpdateHolderResult;
import io.colligence.talken.dex.exception.AccountNotFoundException;
import io.colligence.talken.dex.exception.ActiveAssetHolderAccountNotFoundException;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import io.colligence.talken.dex.exception.UpdateHolderStatusException;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.AssetTypeCreditAlphaNum4;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Server;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;

@Service
@Scope("singleton")
public class ManagedAccountService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(ManagedAccountService.class);

	@Autowired
	private DexSettings dexSettings;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	private SingleKeyTable<String, ManagedAccountPack> maTable = new SingleKeyTable<>();
	private SecureRandom random = new SecureRandom();

	private HashSet<String> checkedAccounts = new HashSet<>();

	@PostConstruct
	private void init() throws AccountNotFoundException {
		for(DexSettings._MasMock masMock : dexSettings.getMasMockUp()) {
			ManagedAccountPack pack = new ManagedAccountPack();
			pack.setCode(masMock.getCode());

			BlockChainPlatformEnum bcp = null;
			for(BlockChainPlatformEnum _bcp : BlockChainPlatformEnum.values()) {
				if(_bcp.name().equalsIgnoreCase(masMock.getPlatform())) bcp = _bcp;
			}
			if(bcp == null) throw new IllegalArgumentException(masMock.getPlatform() + " is not supported.");
			pack.setPlatform(bcp);

			pack.setAssetIssuerAddress(masMock.getAssetIssuer());
			pack.setAssetIssuer(getKeyPair(masMock.getAssetIssuer()));
			pack.setAssetBaseAddress(masMock.getAssetBase());
			pack.setAssetBase(getKeyPair(masMock.getAssetBase()));
			for(String _ah : masMock.getAssetHolder()) {
				pack.addAssetHolder(_ah);
			}
			pack.setDeanchorFeeHolderAddress(masMock.getDeanchorFeeHolder());
			pack.setDeanchorFeeHolder(getKeyPair(masMock.getDeanchorFeeHolder()));
			pack.setOfferFeeHolderAddress(masMock.getOfferFeeHolder());
			pack.setOfferFeeHolder(getKeyPair(masMock.getOfferFeeHolder()));
			pack.setAssetCode(masMock.getCode());
			pack.setAssetType(new AssetTypeCreditAlphaNum4(masMock.getCode(), pack.getAssetIssuer()));
			maTable.insert(pack);
		}
	}

	private KeyPair getKeyPair(String accountID) throws AccountNotFoundException {
		// First, check to make sure that the destination account exists.
		// You could skip this, but if the account does not exist, you will be charged
		// the transaction fee when the transaction fails.
		// It will throw HttpResponseException if account does not exist or there was another error.
		try {
			logger.info("Checking managed account : {}", accountID);

			KeyPair account = KeyPair.fromAccountId(accountID);

			if(!RunningProfile.isLocal() && !checkedAccounts.contains(accountID)) {
				checkedAccounts.add(accountID);
				Server server = stellarNetworkService.pickServer();
				server.accounts().account(account);
			}
			return account;
		} catch(IOException ex) {
			throw new AccountNotFoundException("MA(OnLoad)", accountID);
		}
	}

	private ManagedAccountPack getPack(String assetCode) throws AssetTypeNotFoundException {
		return Optional.ofNullable(maTable.select(assetCode)).orElseThrow(() -> new AssetTypeNotFoundException(assetCode));
	}

	private KeyPair getManagedAccount(String code, ManagedAccountTypeEnum maType) throws AssetTypeNotFoundException {
		getPack(code);
		switch(maType) {
			case ASSET_ISSUER:
				return maTable.select(code).getAssetBase();
			case ASSET_BASE:
				return maTable.select(code).getAssetIssuer();
			case FEECOLLECTOR_DEANCHOR:
				return maTable.select(code).getDeanchorFeeHolder();
			case FEECOLLECTOR_OFFER:
				return maTable.select(code).getOfferFeeHolder();
			default:
				throw new IllegalArgumentException("use getActiveHolderAccountAddress for holder account");
		}
	}

	public AssetTypeCreditAlphaNum4 getAssetType(String code) throws AssetTypeNotFoundException {
		return getPack(code).getAssetType();
	}

	public KeyPair getOfferFeeHolderAccount(String code) throws AssetTypeNotFoundException {
		return getManagedAccount(code, ManagedAccountTypeEnum.FEECOLLECTOR_OFFER);
	}

	public KeyPair getDeanchorFeeHolderAccount(String code) throws AssetTypeNotFoundException {
		return getManagedAccount(code, ManagedAccountTypeEnum.FEECOLLECTOR_DEANCHOR);
	}

	public KeyPair getBaseAccount(String code) throws AssetTypeNotFoundException {
		return getManagedAccount(code, ManagedAccountTypeEnum.ASSET_BASE);
	}

	public String getActiveHolderAccountAddress(String code) throws AssetTypeNotFoundException, ActiveAssetHolderAccountNotFoundException {
		Optional<ManagedAccountPack.AssetHolder> opt_aha = getPack(code).getAssetHolder().stream()
				.filter(ManagedAccountPack.AssetHolder::isActive)
				.findAny();
		if(opt_aha.isPresent()) return opt_aha.get().getAddress();
		else {
			logger.warn("There is no active asset holder account for {}, use random hot account.", code);

			Optional<ManagedAccountPack.AssetHolder> opt_ahh = getPack(code).getAssetHolder().stream()
					.filter(ManagedAccountPack.AssetHolder::isHot)
					.findAny();
			if(opt_ahh.isPresent()) return opt_ahh.get().getAddress();
			else throw new ActiveAssetHolderAccountNotFoundException(code);
		}
	}

	public BlockChainPlatformEnum getAssetPlatform(String code) throws AssetTypeNotFoundException {
		return getPack(code).getPlatform();
	}

	public Hashtable<String, ManagedAccountPack> reload() {
		// TODO : implement reload routine

		return getPackList();
	}

	public Hashtable<String, ManagedAccountPack> getPackList() {
		return maTable.__getRawData();
	}

	public UpdateHolderResult updateHolder(String assetCode, String address, Boolean isHot, Boolean isActive) throws AssetTypeNotFoundException, AccountNotFoundException, UpdateHolderStatusException {

		ManagedAccountPack pack = getPack(assetCode);
		List<ManagedAccountPack.AssetHolder> holders = pack.getAssetHolder();

		Optional<ManagedAccountPack.AssetHolder> opt_ah = holders.stream().filter(_ah -> _ah.getAddress().equals(address)).findAny();
		if(!opt_ah.isPresent()) throw new AccountNotFoundException("Holder", address);

		ManagedAccountPack.AssetHolder holder = opt_ah.get();

		if(isHot != null) {
			if(!isHot) {
				// check this update will remove last hot account
				if(holder.isHot() && holders.stream().filter(ManagedAccountPack.AssetHolder::isHot).count() == 1)
					throw new UpdateHolderStatusException(assetCode, address, "last hot account cannot be freezed.");

				// TODO : update to DB
				holder.setHot(false);
			} else {
				holder.setHot(true);
			}

		}

		if(isActive != null) {
			if(!isActive) {
				// check this update will remove last active account
				if(holder.isActive() && holders.stream().filter(ManagedAccountPack.AssetHolder::isActive).count() == 1)
					throw new UpdateHolderStatusException(assetCode, address, "last active account cannot be deactivated.");

				// TODO : update to DB
				holder.setActive(false);
			} else {

				// TODO : update to DB
				holder.setActive(true);
			}
		}

		UpdateHolderResult result = new UpdateHolderResult();
		result.setManagedAccountPack(pack);
		return result;
	}
}
