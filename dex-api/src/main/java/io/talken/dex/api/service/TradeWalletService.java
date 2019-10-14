package io.talken.dex.api.service;

import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.UserTradeWalletRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.ApiSettings;
import io.talken.dex.api.controller.dto.TradeWalletResult;
import io.talken.dex.shared.exception.SigningException;
import io.talken.dex.shared.exception.StellarException;
import io.talken.dex.shared.exception.TradeWalletCreateFailedException;
import io.talken.dex.shared.service.blockchain.stellar.StellarChannelTransaction;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.blockchain.stellar.StellarSignerTSS;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.tradewallet.TradeWallet;
import io.talken.dex.shared.service.tradewallet.TradeWalletStatus;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.CreateAccountOperation;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.requests.ErrorResponse;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static io.talken.common.persistence.jooq.Tables.USER_TRADE_WALLET;

@Service
@Scope("singleton")
public class TradeWalletService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TradeWalletService.class);

	@Autowired
	private ApiSettings apiSettings;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private SignServerService signServerService;

	private static final BigDecimal minimumBalance = BigDecimal.valueOf(1);
	private static final BigDecimal reserveBufferAmount = BigDecimal.valueOf(1);
	private static final BigDecimal txFeeBufferAmount = BigDecimal.valueOf(0.1);
	private static final BigDecimal reservePerEntry = BigDecimal.valueOf(0.5);
	private static final BigDecimal startingBalance = minimumBalance.add(reserveBufferAmount).add(txFeeBufferAmount);

	private KeyPair masterKey;

	private String creatorAddress;

	@PostConstruct
	private void init() throws Exception {
		masterKey = KeyPair.fromSecretSeed(apiSettings.getIntegration().getSignServer().getAppKey());
		creatorAddress = apiSettings.getTradeWallet().getCreatorAddress();

		try {
			stellarNetworkService.pickServer().accounts().account(apiSettings.getTradeWallet().getCreatorAddress());
		} catch(Exception er) {
			logger.exception(er, "Exception while get creatorAccount information from network.");
			throw new StellarException(er);
		}
	}

	public byte[] getKeyBase(String uid) {
		return masterKey.sign(uid.getBytes(StandardCharsets.UTF_8));
	}

	public BigDecimal getNativeBalance(AccountResponse ar) {
		for(AccountResponse.Balance _bal : ar.getBalances()) {
			if(_bal.getAsset() instanceof AssetTypeNative) {
				return new BigDecimal(_bal.getBalance());
			}
		}
		return null;
	}

	public TradeWalletResult ensureTradeWallet(User user) throws TradeWalletCreateFailedException {
		TradeWalletResult rtn = new TradeWalletResult();
		rtn.setStatus(TradeWalletStatus.NOTEXISTS);

		UserTradeWalletRecord twRecord = dslContext.selectFrom(USER_TRADE_WALLET).where(USER_TRADE_WALLET.USER_ID.eq(user.getId())).fetchOne();

		KeyPair twkp;

		// check wallet
		if(twRecord == null) {
			twRecord = new UserTradeWalletRecord();

			try {
				String walletString = TradeWallet.generate(getKeyBase(user.getUid()));
				twkp = TradeWallet.toKeyPair(getKeyBase(user.getUid()), walletString);
				twRecord.setUserId(user.getId());
				twRecord.setAccountid(twkp.getAccountId());
				twRecord.setSecret(walletString);
				dslContext.attach(twRecord);
				twRecord.store();
			} catch(Exception ex) {
				throw new TradeWalletCreateFailedException(ex, "DB Error");
			}
		} else {
			try {
				twkp = TradeWallet.toKeyPair(getKeyBase(user.getUid()), twRecord.getSecret());
			} catch(Exception ex) {
				throw new TradeWalletCreateFailedException(ex, "Crypto Error");
			}
		}

		rtn.setAccountId(twkp.getAccountId());
		rtn.setStatus(TradeWalletStatus.GENERATED);

		AccountResponse accountResponse;

		// check wallet balance
		try {
			accountResponse = stellarNetworkService.pickServer().accounts().account(twkp.getAccountId());

			rtn.setStatus(TradeWalletStatus.CONFIRMED);
			rtn.setAccountResponse(accountResponse);

			return rtn;
		} catch(ErrorResponse error) {
			if(error.getCode() != 404)
				throw new TradeWalletCreateFailedException(error, error.getBody());
		} catch(IOException ex) {
			logger.exception(ex);
			throw new TradeWalletCreateFailedException(ex, "IO Error");
		}

		// wallet not activated (not created on stellar network)
		// submit create wallet tx if not activated
		SubmitTransactionResponse txResponse;
		try(
				StellarChannelTransaction sctx = stellarNetworkService
						.newChannelTxBuilder()
						.addOperation(
								new CreateAccountOperation
										.Builder(twkp.getAccountId(), startingBalance.stripTrailingZeros().toPlainString())
										.setSourceAccount(creatorAddress)
										.build()
						)
						.addSigner(new StellarSignerTSS(signServerService, creatorAddress)).build()
		) {
			logger.info("Create user({}) tradewallet {} on stellar network.", user.getId(), twkp.getAccountId());
			txResponse = sctx.submit();
		} catch(IOException e) {
			throw new TradeWalletCreateFailedException(e, "IO Error");
		} catch(SigningException e) {
			throw new TradeWalletCreateFailedException(e, "TSS Error");
		}

		if(txResponse.isSuccess()) {
			rtn.setStatus(TradeWalletStatus.ACTIVATED);
			return rtn;
		} else {
			throw new TradeWalletCreateFailedException(txResponse.getExtras().getResultCodes().getTransactionResultCode());
		}
	}
}
