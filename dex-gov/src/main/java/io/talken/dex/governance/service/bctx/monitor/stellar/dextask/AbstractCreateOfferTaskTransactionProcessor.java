package io.talken.dex.governance.service.bctx.monitor.stellar.dextask;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.GovSettings;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessor;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.TokenMetaTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;

/**
 * The type Abstract create offer task transaction processor.
 */
@Component
public abstract class AbstractCreateOfferTaskTransactionProcessor implements DexTaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AbstractCreateOfferTaskTransactionProcessor.class);

	@Autowired
	private GovSettings govSettings;

	@Autowired
	private TokenMetaGovService tmService;

	private TokenMetaTable.ManagedInfo PIVOT_ASSET_MI;
	private BigDecimal feeRatePivot;

	@PostConstruct
	private void init() throws TokenMetaNotFoundException, TokenMetaNotManagedException {
		PIVOT_ASSET_MI = tmService.getManagedInfo(DexSettings.PIVOT_ASSET_CODE);
		feeRatePivot = govSettings.getTask().getCreateOffer().getFeeRatePivot();
	}

    /**
     * Gets pivot asset managed info.
     *
     * @return the pivot asset managed info
     */
    protected TokenMetaTable.ManagedInfo getPivotAssetManagedInfo() {
		return PIVOT_ASSET_MI;
	}

    /**
     * Gets fee rate pivot.
     *
     * @return the fee rate pivot
     */
    protected BigDecimal getFeeRatePivot() {
		return feeRatePivot;
	}
}
