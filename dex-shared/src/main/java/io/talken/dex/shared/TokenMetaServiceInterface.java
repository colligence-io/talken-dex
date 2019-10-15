package io.talken.dex.shared;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import org.stellar.sdk.Asset;

public interface TokenMetaServiceInterface {
	Asset getAssetType(String assetCode) throws TokenMetaNotFoundException;
}
