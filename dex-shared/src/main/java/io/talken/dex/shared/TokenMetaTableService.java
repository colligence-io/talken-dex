package io.talken.dex.shared;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.util.PrefixedLogger;
import org.stellar.sdk.Asset;

import java.util.Map;

public abstract class TokenMetaTableService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaTableService.class);

	private TokenMetaTable tmTable = new TokenMetaTable();
	private TokenMetaTable miTable = new TokenMetaTable();

	public void updateStorage(TokenMetaTable tmTable) {
		TokenMetaTable newMiTable = new TokenMetaTable();

		for(Map.Entry<String, TokenMetaTable.Meta> _kv : tmTable.entrySet()) {
			if(_kv.getValue().isManaged()) {
				_kv.getValue().getManagedInfo().prepareCache();
				newMiTable.put(_kv.getKey(), _kv.getValue());
			}
		}

		this.tmTable = tmTable;
		this.miTable = newMiTable;

		logger.info("Token Meta loaded : all {}, managed {}", tmTable.size(), miTable.size());
	}

	public TokenMetaTable.Meta getTokenMeta(String symbol) throws TokenMetaNotFoundException {
		if(!tmTable.containsKey(symbol.toUpperCase())) throw new TokenMetaNotFoundException(symbol);
		return tmTable.get(symbol.toUpperCase());
	}

	public TokenMetaTable.Meta getTokenMetaManaged(String symbol) throws TokenMetaNotFoundException {
		if(!miTable.containsKey(symbol.toUpperCase())) throw new TokenMetaNotFoundException(symbol);
		return miTable.get(symbol.toUpperCase());
	}

	public TokenMetaTable.ManagedInfo getManagedInfo(String symbol) throws TokenMetaNotManagedException, TokenMetaNotFoundException {
		final TokenMetaTable.Meta meta = getTokenMetaManaged(symbol);
		if(!meta.isManaged()) throw new TokenMetaNotManagedException(symbol);
		return meta.getManagedInfo();
	}

	public Asset getAssetType(String symbol) throws TokenMetaNotFoundException, TokenMetaNotManagedException {
		return getManagedInfo(symbol).dexAssetType();
	}

	public TokenMetaTable getTokenMetaList() {
		return tmTable;
	}

	public TokenMetaTable getTokenMetaManagedList() {
		return miTable;
	}
}
