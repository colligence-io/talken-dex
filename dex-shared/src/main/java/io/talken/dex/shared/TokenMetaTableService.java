package io.talken.dex.shared;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.slack.AdminAlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.stellar.sdk.Asset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The type Token meta table service.
 */
public abstract class TokenMetaTableService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaTableService.class);

	@Autowired
	private AdminAlarmService adminAlarmService;

	private TokenMetaTable tmTable = new TokenMetaTable();
	private TokenMetaTable miTable = new TokenMetaTable();

	private List<TokenMetaTableUpdateEventHandler> updateHandlers = null;

    /**
     * update meta table and fire updateEventHandler
     *
     * @param tmTable the tm table
     */
    public void updateStorage(TokenMetaTable tmTable) {
		TokenMetaTable newMiTable = new TokenMetaTable();

		for(Map.Entry<String, TokenMetaTable.Meta> _kv : tmTable.entrySet()) {
			if(_kv.getValue().isManaged()) {
				_kv.getValue().getManagedInfo().prepareCache();
				newMiTable.put(_kv.getKey(), _kv.getValue());
			}
		}

		if(updateHandlers != null) {
			for(TokenMetaTableUpdateEventHandler updateHandler : updateHandlers) {
				try {
					updateHandler.handleTokenMetaTableUpdate(tmTable);
				} catch(Exception ex) {
					logger.exception(ex, "Exception detected while updating meta data. this may cause unpredictable results.");
				}
			}
		}

		this.tmTable = tmTable;
		this.miTable = newMiTable;

		adminAlarmService.info(logger, "Token Meta loaded : all {}, managed {}", tmTable.size(), miTable.size());

	}

    /**
     * attach update event handler
     *
     * @param handler the handler
     */
    public synchronized void addUpdateEventHandler(TokenMetaTableUpdateEventHandler handler) {
		if(this.updateHandlers == null) this.updateHandlers = new ArrayList<>();

		this.updateHandlers.add(handler);
		// initial run
		handler.handleTokenMetaTableUpdate(this.tmTable);
		logger.info("TokenMetaTableUpdateEventHandler {} registered.", handler.getClass().getSimpleName());
	}

    /**
     * get token meta for symbol
     *
     * @param symbol the symbol
     * @return token meta
     * @throws TokenMetaNotFoundException the token meta not found exception
     */
    public TokenMetaTable.Meta getTokenMeta(String symbol) throws TokenMetaNotFoundException {
		if(!tmTable.containsKey(symbol.toUpperCase())) throw new TokenMetaNotFoundException(symbol);
		return tmTable.get(symbol.toUpperCase());
	}

    /**
     * get token meta managed for symbol
     *
     * @param symbol the symbol
     * @return token meta managed
     * @throws TokenMetaNotFoundException the token meta not found exception
     */
    public TokenMetaTable.Meta getTokenMetaManaged(String symbol) throws TokenMetaNotFoundException {
		if(!miTable.containsKey(symbol.toUpperCase())) throw new TokenMetaNotFoundException(symbol);
		return miTable.get(symbol.toUpperCase());
	}

    /**
     * get managed info for symbol
     *
     * @param symbol the symbol
     * @return managed info
     * @throws TokenMetaNotManagedException the token meta not managed exception
     * @throws TokenMetaNotFoundException   the token meta not found exception
     */
    public TokenMetaTable.ManagedInfo getManagedInfo(String symbol) throws TokenMetaNotManagedException, TokenMetaNotFoundException {
		final TokenMetaTable.Meta meta = getTokenMetaManaged(symbol);
		if(!meta.isManaged()) throw new TokenMetaNotManagedException(symbol);
		return meta.getManagedInfo();
	}

    /**
     * get managed stellar asset type for symbol
     *
     * @param symbol the symbol
     * @return asset type
     * @throws TokenMetaNotFoundException   the token meta not found exception
     * @throws TokenMetaNotManagedException the token meta not managed exception
     */
    public Asset getAssetType(String symbol) throws TokenMetaNotFoundException, TokenMetaNotManagedException {
		return getManagedInfo(symbol).dexAssetType();
	}

    /**
     * full meta table
     *
     * @return token meta list
     */
    public TokenMetaTable getTokenMetaList() {
		return tmTable;
	}

    /**
     * managed meta table
     *
     * @return token meta managed list
     */
    public TokenMetaTable getTokenMetaManagedList() {
		return miTable;
	}
}
