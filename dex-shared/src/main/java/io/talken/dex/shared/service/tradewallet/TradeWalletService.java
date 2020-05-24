package io.talken.dex.shared.service.tradewallet;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.UserTradeWalletRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TokenMetaTableService;
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

import static io.talken.common.persistence.jooq.Tables.USER;
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

	private final TokenMetaTableService tmService;

	private static final BigDecimal minimumBalance = BigDecimal.valueOf(1);
	private static final BigDecimal reserveBufferAmount = BigDecimal.valueOf(1);
	private static final BigDecimal txFeeBufferAmount = BigDecimal.valueOf(0.1);
	private static final BigDecimal reservePerEntry = BigDecimal.valueOf(0.5);
	private static final BigDecimal startingBalance = minimumBalance.add(reserveBufferAmount).add(txFeeBufferAmount);

	private static final int confirmInterval = 4000;
	private static final int confirmMaxRetry = 5;

	private KeyPair masterKey;

	private String creatorAddress;

	@PostConstruct
	private void init() throws Exception {
		this.masterKey = KeyPair.fromSecretSeed(dexSettings.getIntegration().getSignServer().getAppKey());
		this.creatorAddress = dexSettings.getTradeWallet().getCreatorAddress();

		// check creator account is available
		stellarNetworkService.pickServer().accounts().account(creatorAddress);
	}

	/**
	 * TradeWallet keyBase(uses signServerKey for convenience and easy maintenance)
	 *
	 * @param uid
	 * @return
	 */
	private byte[] getKeyBase(String uid) {
		return masterKey.sign(uid.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * generate tradeWalletString with user individual key (UID for example, can be any unique string per wallet)
	 *
	 * @param individualKey
	 * @return
	 * @throws NoSuchPaddingException
	 * @throws InvalidKeyException
	 * @throws NoSuchAlgorithmException
	 * @throws IllegalBlockSizeException
	 * @throws WalletException
	 * @throws BadPaddingException
	 * @throws InvalidAlgorithmParameterException
	 */
	private String generateSecret(String individualKey) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, WalletException, BadPaddingException, InvalidAlgorithmParameterException {
		return TradeWallet.generate(getKeyBase(individualKey));
	}

	/**
	 * decrypt tradeWalletString with user individual key and masterKeyBase
	 *
	 * @param individualKey
	 * @param secret
	 * @return
	 * @throws NoSuchPaddingException
	 * @throws InvalidKeyException
	 * @throws NoSuchAlgorithmException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws InvalidAlgorithmParameterException
	 */
	private KeyPair decryptSecret(String individualKey, String secret) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
		return TradeWallet.toKeyPair(getKeyBase(individualKey), secret);
	}

	/**
	 * extract Stellar keypair from user tradeWalletInfo
	 *
	 * @param tradeWallet
	 * @return
	 * @throws SigningException
	 */
	public KeyPair extractKeyPair(TradeWalletInfo tradeWallet) throws SigningException {
		try {
			return decryptSecret(tradeWallet.getUid(), tradeWallet.getSecret());
		} catch(Exception ex) {
			throw new SigningException(ex, tradeWallet.getAccountId(), "Signing Crypto Error");
		}
	}

	public TradeWalletInfo getTradeWallet(String walletAddr) throws TradeWalletCreateFailedException {
		User user = dslContext.select(USER.asterisk())
				.from(USER.leftOuterJoin(USER_TRADE_WALLET).on(USER.ID.eq(USER_TRADE_WALLET.USER_ID)))
				.where(USER_TRADE_WALLET.ACCOUNTID.eq(walletAddr))
				.fetchOneInto(User.class);

		if(user == null) throw new TradeWalletCreateFailedException("User trade wallet " + walletAddr + " not found");

		return loadTradeWallet(user, false);
	}

	public TradeWalletInfo getTradeWallet(User user) throws TradeWalletCreateFailedException {
		return loadTradeWallet(user, false);
	}

	public TradeWalletInfo ensureTradeWallet(User user) throws TradeWalletCreateFailedException {
		return loadTradeWallet(user, true);
	}

	private TradeWalletInfo loadTradeWallet(User user, boolean ensure) throws TradeWalletCreateFailedException {
		UserTradeWalletRecord twRecord = dslContext.selectFrom(USER_TRADE_WALLET).where(USER_TRADE_WALLET.USER_ID.eq(user.getId())).fetchOne();
		TradeWalletInfo rtn = new TradeWalletInfo(user);
		rtn.setConfirmed(false);

		String walletString;
		String accountId;
		if(twRecord == null) {
			// generate address
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
		}

		try {
			walletString = twRecord.getSecret();
			accountId = decryptSecret(user.getUid(), walletString).getAccountId();
		} catch(Exception ex) {
			throw new TradeWalletCreateFailedException(ex, "Crypto Error");
		}
		rtn.setAccountId(accountId);
		rtn.setSecret(walletString);

		// activationconfirmed status
		// null -> not confirmed, not requested
		// false -> not confirmed, but requested
		// true -> confirmed


		// return null for not activated wallet to avoid meaningless stellar call
		if(!ensure && twRecord.getActivationconfirmed() == null) {
			rtn.setConfirmed(false);
			rtn.setAccountResponse(null);
			return rtn;
		}

		AccountResponse ar = getAccountInfoFromStellar(accountId);
		if(ar != null) {
			if(twRecord.getActivationconfirmed() == null || !twRecord.getActivationconfirmed()) { // set confirmed as true if accountResponse is OK and DB status is false
				twRecord.setActivationconfirmed(true);
				twRecord.store();
			}
			rtn.setConfirmed(true);
			rtn.setAccountResponse(ar);
			return rtn;
		}

		// ar == null
		if(!ensure) {
			rtn.setConfirmed(false);
			rtn.setAccountResponse(null);
			return rtn;
		}

		// FROM NOW ON
		// accountResponse is null && ensure is on

		// refresh twRecord for sure
		//twRecord.refresh();

		// if activation is not initiated, send activation tx
		if(twRecord.getActivationconfirmed() == null) {
			// submit create wallet tx if not activated
			logger.info("Create tradewallet {} for user #{} on stellar network.", accountId, user.getId());
			StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService
					.newChannelTxBuilder()
					.addOperation(
							new CreateAccountOperation
									.Builder(accountId, startingBalance.stripTrailingZeros().toPlainString())
									.setSourceAccount(creatorAddress)
									.build()
					)
					.addSigner(new StellarSignerTSS(signServerService, creatorAddress));

			SubmitTransactionResponse txResponse;
			try(StellarChannelTransaction sctx = sctxBuilder.build()) {
				twRecord.setActivationtxhash(ByteArrayUtil.toHexString(sctx.getTx().hash()));
				twRecord.setActivationconfirmed(false);
				twRecord.store();
				txResponse = sctx.submit();

				if(!txResponse.isSuccess()) {
					ObjectPair<String, String> errorInfo = StellarConverter.getResultCodesFromExtra(txResponse);
					throw new TradeWalletCreateFailedException(errorInfo.first() + " : " + errorInfo.second());
				}
			} catch(IOException e) {
				throw new TradeWalletCreateFailedException(e, "IO Error");
			} catch(SigningException e) {
				throw new TradeWalletCreateFailedException(e, "TSS Error");
			} catch(AccountRequiresMemoException e) {
				throw new TradeWalletCreateFailedException(e, "STELLAR MEMO REQUIRED");
			}
		}

		// activation transaction is sent successfully
		// do confirmation
		for(int i = 0; i < confirmMaxRetry; i++) {
			// check stellar account balance
			AccountResponse accountResponse = getAccountInfoFromStellar(accountId);

			if(accountResponse != null) {
				twRecord.setActivationconfirmed(true);
				twRecord.store();

				rtn.setConfirmed(true);
				rtn.setAccountResponse(accountResponse);
				return rtn;
			}

			// wait for next check
			try {
				// FIXME : this can be serious bottle-neck point when called from single thread process
				logger.debug("Wait for tradeWallet confirmation {} {} ... {}", user.getId(), accountId, i);
				Thread.sleep(confirmInterval);
			} catch(InterruptedException iex) {
				throw new TradeWalletCreateFailedException(iex, "Interrupted");
			}
		}

		// if cannot confirm, throw exception
		throw new TradeWalletCreateFailedException("Cannot confirm trade wallet from stellar network");
	}

	private AccountResponse getAccountInfoFromStellar(String accountId) throws TradeWalletCreateFailedException {
		// check stellar account balance
		AccountResponse accountResponse = null;
		try {
			accountResponse = stellarNetworkService.pickServer().accounts().account(accountId);
		} catch(ErrorResponse error) {
			if(error.getCode() != 404) { // only error 404 can pass this block, otherwise throw
				logger.exception(error);
				throw new TradeWalletCreateFailedException(error, error.getBody());
			}
		} catch(IOException ex) {
			logger.exception(ex);
			throw new TradeWalletCreateFailedException(ex, "IO Error");
		} catch(Exception ex) {
			logger.exception(ex);
			throw new TradeWalletCreateFailedException(ex, "Unknown Error");
		}

		// 404 or OK can reach here
		return accountResponse;
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
	public ObjectPair<Boolean, BigDecimal> addNativeBalancingOperation(StellarChannelTransaction.Builder sctxBuilder, TradeWalletInfo tradeWallet, boolean plusOneEntry, String... assetCodes) throws TokenMetaNotFoundException, TradeWalletRebalanceException, TokenMetaNotManagedException {
		boolean added = false;

		BigDecimal nativeBalance = tradeWallet.getNativeBalance();

		BigDecimal currentReserve = minimumBalance.add(reservePerEntry.multiply(BigDecimal.valueOf(tradeWallet.getAccountResponse().getSubentryCount())));

		List<Asset> haveToTrust = new ArrayList<>();
		if(assetCodes != null) {
			for(String assetCode : assetCodes) {
				Asset assetType = tmService.getAssetType(assetCode);
				if(!tradeWallet.isTrusted(assetType)) haveToTrust.add(assetType);
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

	public void resetTradeWallet(String uid) throws TradeWalletCreateFailedException, SigningException {
		User user = dslContext.selectFrom(USER).where(USER.UID.eq(uid)).fetchOneInto(User.class);

		if(user == null) throw new TradeWalletCreateFailedException("User " + uid + " does not exists");

		resetTradeWallet(user);
	}

	/**
	 * claim all assets, merge into issuer, delete from db
	 *
	 * @param user
	 * @return
	 * @throws TradeWalletCreateFailedException
	 * @throws SigningException
	 */
	public boolean resetTradeWallet(User user) throws TradeWalletCreateFailedException, SigningException {

		TradeWalletInfo tw = loadTradeWallet(user, false);

		KeyPair kp = extractKeyPair(tw);

		Server server = stellarNetworkService.pickServer();

		AccountResponse account = null;

		try {
			account = server.accounts().account(kp.getAccountId());
		} catch(Exception ex) {
			if(ex instanceof ErrorResponse) {
				if(((ErrorResponse) ex).getCode() != 404) {
					logger.exception(ex, "{} {}", ((ErrorResponse) ex).getCode(), ((ErrorResponse) ex).getBody());
					return false;
				}
			} else {
				logger.exception(ex);
				return false;
			}
		}

		try {
			// merge account if account is created at stellar network
			if(account != null) {
				Transaction.Builder txBuilder = new Transaction.Builder(account, stellarNetworkService.getNetwork())
						.setBaseFee(stellarNetworkService.getNetworkFee())
						.setTimeout(30);

				boolean sendTx = false;

				for(AccountResponse.Balance balance : account.getBalances()) {
					if(!(balance.getAsset() instanceof AssetTypeNative)) {
						sendTx = true;
						if(new BigDecimal(balance.getBalance()).compareTo(BigDecimal.ZERO) > 0) {
							txBuilder.addOperation(
									new PaymentOperation.Builder(balance.getAssetIssuer(), balance.getAsset(), balance.getBalance()).build()
							);
						}
						txBuilder.addOperation(
								new ChangeTrustOperation.Builder(balance.getAsset(), "0").build()
						);
					}
				}

				if(sendTx) {
					Transaction tx = txBuilder.build();
					tx.sign(kp);
					SubmitTransactionResponse paymentResponse = stellarNetworkService.sendTransaction(server, tx);

					if(!paymentResponse.isSuccess()) {
						ObjectPair<String, String> resultCodesFromExtra = StellarConverter.getResultCodesFromExtra(paymentResponse);
						logger.info("Withdraw user {} tradewallet {} failed : {} {}", user.getUid(), kp.getAccountId(), resultCodesFromExtra.first(), resultCodesFromExtra.second());
						return false;
					}

					logger.info("Withdraw user {} tradewallet {} : {}", user.getUid(), kp.getAccountId(), paymentResponse.getHash());
				}

				Transaction mtx = new Transaction.Builder(account, stellarNetworkService.getNetwork())
						.setBaseFee(stellarNetworkService.getNetworkFee())
						.setTimeout(30)
						.addOperation(new AccountMergeOperation.Builder(this.creatorAddress).build())
						.build();

				mtx.sign(kp);
				SubmitTransactionResponse mergeResponse = stellarNetworkService.sendTransaction(server, mtx);

				if(!mergeResponse.isSuccess()) {
					ObjectPair<String, String> resultCodesFromExtra = StellarConverter.getResultCodesFromExtra(mergeResponse);
					logger.info("Merge user {} tradewallet {} failed : {} {}", user.getUid(), kp.getAccountId(), resultCodesFromExtra.first(), resultCodesFromExtra.second());
					return false;
				}

				logger.info("Merge user {} tradewallet {} : {}", user.getUid(), kp.getAccountId(), mergeResponse.getHash());
			}

			int count = dslContext.deleteFrom(USER_TRADE_WALLET).where(USER_TRADE_WALLET.USER_ID.eq(user.getId())).execute();

			if(count > 0) {
				logger.info("Removed user {} tradewallet {} from db.", user.getUid(), kp.getAccountId());
			}

		} catch(Exception ex) {
			if(ex instanceof ErrorResponse)
				logger.exception(ex, "{} {}", ((ErrorResponse) ex).getCode(), ((ErrorResponse) ex).getBody());
			else
				logger.exception(ex);

			return false;
		}
		return true;
	}


	/**
	 * Rebalance user tradewallet asset balance (not for native XLM)
	 *
	 * @param user
	 * @param assetCode
	 * @param targetBalance targetBalance, (untrust if negative)
	 * @return
	 * @throws TradeWalletCreateFailedException
	 * @throws TradeWalletRebalanceException
	 * @throws SigningException
	 * @throws TokenMetaNotFoundException
	 * @throws TokenMetaNotManagedException
	 */
	public boolean rebalanceIssuedAsset(User user, String assetCode, BigDecimal targetBalance) throws TradeWalletCreateFailedException, TradeWalletRebalanceException, SigningException, TokenMetaNotFoundException, TokenMetaNotManagedException {
		TradeWalletInfo tw = loadTradeWallet(user, false);

		KeyPair kp = extractKeyPair(tw);

		Server server = stellarNetworkService.pickServer();

		AccountResponse account = null;

		try {
			account = server.accounts().account(kp.getAccountId());
		} catch(Exception ex) {
			if(ex instanceof ErrorResponse) {
				if(((ErrorResponse) ex).getCode() != 404) {
					logger.exception(ex, "{} {}", ((ErrorResponse) ex).getCode(), ((ErrorResponse) ex).getBody());
					return false;
				}
			} else {
				logger.exception(ex);
				return false;
			}
		}

		if(account == null) throw new TradeWalletRebalanceException("Cannot get target account from stellar network");

		TokenMetaTable.ManagedInfo managedInfo = tmService.getManagedInfo(assetCode);

		BigDecimal adjustAmount = null;

		boolean untrust = false;
		if(targetBalance.compareTo(BigDecimal.ZERO) < 0) {
			untrust = true;
			targetBalance = BigDecimal.ZERO;
		}

		for(AccountResponse.Balance balance : account.getBalances()) {
			if(!(balance.getAsset() instanceof AssetTypeNative)) {
				if(balance.getAsset().equals(managedInfo.dexAssetType())) {
					BigDecimal remainBalance = StellarConverter.scale(new BigDecimal(balance.getBalance()));
					adjustAmount = targetBalance.subtract(remainBalance);
				}
			}
		}

		if(adjustAmount == null)
			throw new TradeWalletRebalanceException(tw.getAccountId() + " does not have " + managedInfo.getAssetCode());


		try {
			StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();

			if(adjustAmount.compareTo(BigDecimal.ZERO) > 0) {
				sctxBuilder.addOperation(
						new PaymentOperation.Builder(tw.getAccountId(), managedInfo.dexAssetType(), StellarConverter.actualToString(adjustAmount))
								.setSourceAccount(managedInfo.getIssuerAddress())
								.build()
				);

				sctxBuilder.addSigner(new StellarSignerTSS(signServerService, managedInfo.getIssuerAddress()));
			} else {
				if(adjustAmount.compareTo(BigDecimal.ZERO) < 0) {
					sctxBuilder.addOperation(
							new PaymentOperation.Builder(managedInfo.getIssuerAddress(), managedInfo.dexAssetType(), StellarConverter.actualToString(adjustAmount.abs()))
									.setSourceAccount(tw.getAccountId())
									.build()
					);
				}

				if(untrust) {
					sctxBuilder.addOperation(
							new ChangeTrustOperation.Builder(managedInfo.dexAssetType(), "0")
									.setSourceAccount(tw.getAccountId())
									.build()
					);
				}

				sctxBuilder.addSigner(new StellarSignerAccount(extractKeyPair(tw)));
			}

			SubmitTransactionResponse response = sctxBuilder.buildAndSubmit();

			if(!response.isSuccess()) {
				ObjectPair<String, String> resultCodesFromExtra = StellarConverter.getResultCodesFromExtra(response);
				logger.info("Rebalance user {} tradewallet {} {} failed : {} {}", user.getUid(), kp.getAccountId(), assetCode, resultCodesFromExtra.first(), resultCodesFromExtra.second());
				return false;
			}

			logger.info("Rebalance user {} tradewallet {} -> {} {}{} : {}", user.getUid(), kp.getAccountId(), targetBalance, assetCode, (untrust) ? " (untrust)" : "", response.getHash());

		} catch(Exception ex) {
			if(ex instanceof ErrorResponse)
				logger.exception(ex, "{} {}", ((ErrorResponse) ex).getCode(), ((ErrorResponse) ex).getBody());
			else
				logger.exception(ex);

			return false;
		}

		return true;
	}
}
