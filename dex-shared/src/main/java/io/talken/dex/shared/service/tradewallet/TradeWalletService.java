package io.talken.dex.shared.service.tradewallet;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.UserTradeWalletRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.TokenMetaServiceInterface;
import io.talken.dex.shared.exception.SigningException;
import io.talken.dex.shared.exception.TradeWalletCreateFailedException;
import io.talken.dex.shared.exception.TradeWalletRebalanceException;
import io.talken.dex.shared.service.blockchain.stellar.*;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.tradewallet.wallet.WalletException;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.requests.ErrorResponse;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import javax.annotation.PostConstruct;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static io.talken.common.persistence.jooq.Tables.USER_TRADE_WALLET;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class TradeWalletService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TradeWalletService.class);

	private final DexSettings dexSettings;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private SignServerService signServerService;

	private final TokenMetaServiceInterface tmService;

	private static final BigDecimal minimumBalance = BigDecimal.valueOf(1);
	private static final BigDecimal reserveBufferAmount = BigDecimal.valueOf(1);
	private static final BigDecimal txFeeBufferAmount = BigDecimal.valueOf(0.1);
	private static final BigDecimal reservePerEntry = BigDecimal.valueOf(0.5);
	private static final BigDecimal startingBalance = minimumBalance.add(reserveBufferAmount).add(txFeeBufferAmount);

	private static int confirmInterval = 4000;
	private static int confirmMaxRetry = 5;

	private KeyPair masterKey;

	private String creatorAddress;

	@PostConstruct
	private void init() throws Exception {
		this.masterKey = KeyPair.fromSecretSeed(dexSettings.getIntegration().getSignServer().getAppKey());
		this.creatorAddress = dexSettings.getTradeWallet().getCreatorAddress();

		// check creator account is available
		stellarNetworkService.pickServer().accounts().account(creatorAddress);
	}

	private byte[] getKeyBase(String uid) {
		return masterKey.sign(uid.getBytes(StandardCharsets.UTF_8));
	}

	private String generateSecret(String individualKey) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, WalletException, BadPaddingException, InvalidAlgorithmParameterException {
		return TradeWallet.generate(getKeyBase(individualKey));
	}

	private KeyPair decryptSecret(String individualKey, String secret) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
		return TradeWallet.toKeyPair(getKeyBase(individualKey), secret);
	}

	public KeyPair extractKeyPair(TradeWalletInfo tradeWallet) throws SigningException {
		try {
			return decryptSecret(tradeWallet.getUid(), tradeWallet.getSecret());
		} catch(Exception ex) {
			throw new SigningException(ex, tradeWallet.getAccountId(), "Signing Crypto Error");
		}
	}

	public BigDecimal getNativeBalance(AccountResponse ar) {
		for(AccountResponse.Balance _bal : ar.getBalances()) {
			if(_bal.getAsset() instanceof AssetTypeNative) {
				return new BigDecimal(_bal.getBalance());
			}
		}
		return null;
	}

	public boolean isTrusted(AccountResponse ar, Asset asset) {
		for(AccountResponse.Balance _bal : ar.getBalances()) {
			if(_bal.getAsset().equals(asset)) return true;
		}
		return false;
	}

	public TradeWalletInfo getTradeWallet(User user) throws TradeWalletCreateFailedException {
		return loadTradeWallet(user, false);
	}

	public TradeWalletInfo ensureTradeWallet(User user) throws TradeWalletCreateFailedException {
		return loadTradeWallet(user, true);
	}

	private TradeWalletInfo loadTradeWallet(User user, boolean ensure) throws TradeWalletCreateFailedException {
		UserTradeWalletRecord twRecord = dslContext.selectFrom(USER_TRADE_WALLET).where(USER_TRADE_WALLET.USER_ID.eq(user.getId())).fetchOne();
		TradeWalletInfo rtn = new TradeWalletInfo(user.getUid());
		rtn.setConfirmed(false);

		String walletString;
		String accountId;
		if(twRecord == null) {
			// return as not confirmed status
			if(!ensure) return rtn;

			// generate address when ensure is true
			twRecord = new UserTradeWalletRecord();

			try {
				walletString = generateSecret(user.getUid());
				accountId = decryptSecret(user.getUid(), walletString).getAccountId();
				twRecord.setUserId(user.getId());
				twRecord.setAccountid(accountId);
				twRecord.setSecret(walletString);
				dslContext.attach(twRecord);
				twRecord.store();
			} catch(Exception ex) {
				throw new TradeWalletCreateFailedException(ex, "DB Error");
			}
		} else {
			try {
				walletString = twRecord.getSecret();
				accountId = decryptSecret(user.getUid(), walletString).getAccountId();
			} catch(Exception ex) {
				throw new TradeWalletCreateFailedException(ex, "Crypto Error");
			}
		}
		rtn.setAccountId(accountId);
		rtn.setSecret(walletString);


		boolean activationRequested = false;

		for(int i = 0; i < confirmMaxRetry; i++) {
			// check stellar account balance
			AccountResponse accountResponse = null;
			try {
				accountResponse = stellarNetworkService.pickServer().accounts().account(accountId);
			} catch(IOException ex) {
				logger.exception(ex);
				throw new TradeWalletCreateFailedException(ex, "IO Error");
			} catch(ErrorResponse error) {
				if(error.getCode() != 404) {
					logger.exception(error);
					throw new TradeWalletCreateFailedException(error, error.getBody());
				}
			}

			if(accountResponse != null) {
				rtn.setConfirmed(true);
				rtn.setAccountResponse(accountResponse);
				return rtn;
			}

			if(!activationRequested) {
				// only ErrorResponse 404 can reach here
				// so wallet is not activated yet. (not created on stellar network)
				// submit create wallet tx if not activated
				SubmitTransactionResponse txResponse;
				try {
					logger.info("Create tradewallet {} for user #{} on stellar network.", accountId, user.getId());
					txResponse = stellarNetworkService
							.newChannelTxBuilder()
							.addOperation(
									new CreateAccountOperation
											.Builder(accountId, startingBalance.stripTrailingZeros().toPlainString())
											.setSourceAccount(creatorAddress)
											.build()
							)
							.addSigner(new StellarSignerTSS(signServerService, creatorAddress))
							.buildAndSubmit();
				} catch(IOException e) {
					throw new TradeWalletCreateFailedException(e, "IO Error");
				} catch(SigningException e) {
					throw new TradeWalletCreateFailedException(e, "TSS Error");
				}

				if(!txResponse.isSuccess())
					throw new TradeWalletCreateFailedException(txResponse.getExtras().getResultCodes().getTransactionResultCode());

				activationRequested = true;
			}

			// wait for next check
			try {
				// FIXME : this can be serious bottle-neck point when called from single thread process
				Thread.sleep(confirmInterval);
			} catch(InterruptedException iex) {
				throw new TradeWalletCreateFailedException(iex, "Interrupted");
			}
		}

		throw new TradeWalletCreateFailedException("Cannot confirm trade wallet");
	}

	/**
	 * Add rebalance operation and signer to SCTX Builder
	 *
	 * @param sctxBuilder  SCTX Builder
	 * @param tradeWallet
	 * @param plusOneEntry set true if one more entry is required (e.g before making offer entry)
	 * @param assetCodes   asset codes to check trustline
	 * @return (tx modified, rebalace amount)
	 * @throws TokenMetaNotFoundException
	 */
	public ObjectPair<Boolean, BigDecimal> addNativeBalancingOperation(StellarChannelTransaction.Builder sctxBuilder, TradeWalletInfo tradeWallet, boolean plusOneEntry, String... assetCodes) throws TokenMetaNotFoundException, TradeWalletRebalanceException {
		boolean added = false;

		BigDecimal nativeBalance = getNativeBalance(tradeWallet.getAccountResponse());

		BigDecimal currentReserve = minimumBalance.add(reservePerEntry.multiply(BigDecimal.valueOf(tradeWallet.getAccountResponse().getSubentryCount())));

		List<Asset> haveToTrust = new ArrayList<>();
		if(assetCodes != null) {
			for(String assetCode : assetCodes) {
				Asset assetType = tmService.getAssetType(assetCode);
				if(!isTrusted(tradeWallet.getAccountResponse(), assetType)) haveToTrust.add(assetType);
			}
		}

		int plusEntry = ((plusOneEntry) ? 1 : 0) + haveToTrust.size();

		BigDecimal requiredBalance = currentReserve
				.add(reservePerEntry.multiply(BigDecimal.valueOf(plusEntry)))
				.add(reserveBufferAmount);

		if(haveToTrust.size() > 0) {
			// add change trust operations
			for(Asset asset : haveToTrust) {
				sctxBuilder.addOperation(
						new ChangeTrustOperation
								.Builder(asset, String.valueOf(StellarConverter.rawToActualString(BigInteger.valueOf(Long.MAX_VALUE))))
								.setSourceAccount(tradeWallet.getAccountId())
								.build()
				);

				try {
					sctxBuilder.addSigner(new StellarSignerAccount(decryptSecret(tradeWallet.getUid(), tradeWallet.getSecret())));
				} catch(Exception ex) {
					throw new TradeWalletRebalanceException(ex, "trade wallet signer fault");
				}

				added = true;
			}
		}

		BigDecimal refillAmount = BigDecimal.ZERO;
		if(nativeBalance.compareTo(requiredBalance) <= 0) {
			refillAmount = requiredBalance.subtract(nativeBalance).add(txFeeBufferAmount);

			sctxBuilder
					.addOperation(
							new PaymentOperation
									.Builder(tradeWallet.getAccountId(), new AssetTypeNative(), refillAmount.stripTrailingZeros().toPlainString())
									.setSourceAccount(creatorAddress)
									.build()
					)
					.addSigner(new StellarSignerTSS(signServerService, creatorAddress));

			added = true;
		}

		return new ObjectPair<>(added, refillAmount);
	}
}
