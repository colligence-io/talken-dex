package io.colligence.talken.dex.service;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.exception.StellarAccountNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Server;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;

@Service
@Scope("singleton")
public class TxFeeService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TxFeeService.class);

	@Autowired
	private DexSettings dexSettings;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	private HashMap<DexTxTypeEnum, KeyPair> feeHolderAccountMap = new HashMap<>();

	@PostConstruct
	private void init() throws StellarAccountNotFoundException {
		addHolderAccount(DexTxTypeEnum.OFFER, KeyPair.fromAccountId(dexSettings.getFee().getOfferFeeHolderAccount()));
		addHolderAccount(DexTxTypeEnum.DEANCHOR, KeyPair.fromAccountId(dexSettings.getFee().getDeanchorFeeHolderAccount()));
	}

	private void addHolderAccount(DexTxTypeEnum txType, KeyPair account) throws StellarAccountNotFoundException {

		// First, check to make sure that the destination account exists.
		// You could skip this, but if the account does not exist, you will be charged
		// the transaction fee when the transaction fails.
		// It will throw HttpResponseException if account does not exist or there was another error.
		try {
			Server server = stellarNetworkService.pickServer();
			server.accounts().account(account);
			feeHolderAccountMap.put(txType, account);
		} catch(IOException ex) {
			throw new StellarAccountNotFoundException("fee holder account invalid : {}", ex.getMessage());
		}
	}

	public double calculateOfferFee(String assetCode, double amount) {
		if(assetCode.equals("CTX")) return amount * dexSettings.getFee().getOfferFeeRateForCTX();
		else return amount * dexSettings.getFee().getOfferFeeRate();
	}

	public KeyPair getOfferFeeHolderAccount() {
		return feeHolderAccountMap.get(DexTxTypeEnum.OFFER);
	}
}
