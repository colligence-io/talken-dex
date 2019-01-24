package io.colligence.talken.dex.api.mas.ma;

import io.colligence.talken.common.RunningProfile;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.collection.SingleKeyTable;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import io.colligence.talken.dex.exception.StellarAccountNotFoundException;
import io.colligence.talken.common.persistence.enums.BlockChainPlatformEnum;
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
	private void init() throws StellarAccountNotFoundException {
		for(DexSettings._MasMock masMock : dexSettings.getMasMockUp()) {
			ManagedAccountPack pack = new ManagedAccountPack();
			pack.setCode(masMock.getCode());

			BlockChainPlatformEnum bcp = null;
			for(BlockChainPlatformEnum _bcp : BlockChainPlatformEnum.values()) {
				if(_bcp.name().equalsIgnoreCase(masMock.getPlatform())) bcp = _bcp;
			}
			if(bcp == null) throw new IllegalArgumentException(masMock.getPlatform() + " is not supported.");
			pack.setPlatform(bcp);
			pack.setAssetIssuer(getKeyPair(masMock.getAssetIssuer()));
			pack.setAssetBase(getKeyPair(masMock.getAssetBase()));
			pack.setAssetHolder(masMock.getAssetHolder());
			pack.setDeanchorFeeHolder(getKeyPair(masMock.getDeanchorFeeHolder()));
			pack.setOfferFeeHolder(getKeyPair(masMock.getOfferFeeHolder()));
			pack.setAssetType(new AssetTypeCreditAlphaNum4(masMock.getCode(), pack.getAssetIssuer()));
			maTable.insert(pack);
		}
	}

	private KeyPair getKeyPair(String accountID) throws StellarAccountNotFoundException {
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
			throw new StellarAccountNotFoundException("MA", accountID);
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
				throw new IllegalArgumentException("use getHolderAccountAddress for holder account");
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

	public String getHolderAccountAddress(String code) throws AssetTypeNotFoundException {
		return getPack(code).getAssetHolder().get(random.nextInt(maTable.select(code).getAssetHolder().size()));
	}

	public BlockChainPlatformEnum getAssetPlatform(String code) throws AssetTypeNotFoundException {
		return getPack(code).getPlatform();
	}

	public SingleKeyTable<String, ManagedAccountPack> getPackList() {
		return maTable;
	}
}
