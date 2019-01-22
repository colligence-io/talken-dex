package io.colligence.talken.dex.service;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("singleton")
public class TxFeeService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TxFeeService.class);

	@Autowired
	private DexSettings dexSettings;

	public double calculateOfferFee(String assetCode, double amount) {
		if(assetCode.equals("CTX")) return amount * dexSettings.getFee().getOfferFeeRateForCTX();
		else return amount * dexSettings.getFee().getOfferFeeRate();
	}
}
