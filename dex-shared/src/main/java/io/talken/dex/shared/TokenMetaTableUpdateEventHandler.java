package io.talken.dex.shared;

/**
 * The interface Token meta table update event handler.
 */
public interface TokenMetaTableUpdateEventHandler {
    /**
     * fired after token meta table update
     * should attached with TokenMetaTableService bean using TokenMetaTableService.addUpdateEventHandler
     *
     * @param metaTable the meta table
     */
    void handleTokenMetaTableUpdate(TokenMetaTable metaTable);
}
