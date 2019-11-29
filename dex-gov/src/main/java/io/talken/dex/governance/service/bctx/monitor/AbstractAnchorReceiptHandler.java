package io.talken.dex.governance.service.bctx.monitor;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TokenMetaTableService;
import io.talken.dex.shared.TokenMetaTableUpdateEventHandler;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractAnchorReceiptHandler implements TokenMetaTableUpdateEventHandler {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AbstractAnchorReceiptHandler.class);

	private Set<String> holderAddresses = null;
	private Set<BlockChainPlatformEnum> bcTypes = new HashSet<>();

	@Autowired
	private TokenMetaTableService tmtService;

	@PostConstruct
	private void attach() {
		tmtService.addUpdateEventHandler(this);
	}

	@Override
	public synchronized void handleTokenMetaTableUpdate(TokenMetaTable metaTable) {
		holderAddresses = new HashSet<>();
		for(TokenMetaTable.Meta meta : metaTable.values()) {
			if(meta.isManaged()) {
				if(bcTypes.contains(meta.getBctxType())) {
					for(TokenMetaTable.HolderAccountInfo assetHolderAccount : meta.getManagedInfo().getAssetHolderAccounts()) {
						holderAddresses.add(assetHolderAccount.getAddress());
					}
				}
			}
		}
	}

	protected synchronized void addBcType(BlockChainPlatformEnum bcType) {
		bcTypes.add(bcType);
	}

	protected synchronized boolean checkHolder(String holderAddress) {
		// do not check holder address and return true for all addresses.
		// this is intended to sure anchor task working even if meta update is failed.
		if(holderAddresses == null || holderAddresses.isEmpty()) return true;
		else return holderAddresses.contains(holderAddress);
	}
}
