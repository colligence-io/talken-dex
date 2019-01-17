package io.colligence.talken.dex.service;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.AssetTypeCreditAlphaNum4;
import org.stellar.sdk.KeyPair;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Optional;

@Service
@Scope("singleton")
public class AssetTypeService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AssetTypeService.class);

	@Autowired
	private DexSettings dexSettings;

	private HashMap<String, AssetTypeCreditAlphaNum4> assetMap = new HashMap<>();

	@PostConstruct
	private void init() {
		for(DexSettings._AssetType _assetType : dexSettings.getAssetTypeList()) {
			logger.debug("AssetType : {} / {} loaded", _assetType.getCode(), _assetType.getIssuer());
			assetMap.put(_assetType.getCode(), new AssetTypeCreditAlphaNum4(_assetType.getCode(), KeyPair.fromAccountId(_assetType.getIssuer())));
		}
	}

	public AssetTypeCreditAlphaNum4 getAssetType(String assetCode) throws AssetTypeNotFoundException {
		return Optional.ofNullable(assetMap.get(assetCode)).orElseThrow(() -> new AssetTypeNotFoundException(assetCode));
	}
}
