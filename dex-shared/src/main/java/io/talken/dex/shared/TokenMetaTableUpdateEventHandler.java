package io.talken.dex.shared;

public interface TokenMetaTableUpdateEventHandler {
	/**
	 * fired after token meta table update
	 * should attached with TokenMetaTableService bean using TokenMetaTableService.addUpdateEventHandler
	 *
	 * @param metaTable
	 */
	void handleTokenMetaTableUpdate(TokenMetaTable metaTable);
}
