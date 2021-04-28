package io.talken.dex.api.service;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import com.google.gson.JsonObject;
import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.GeneralException;
import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.persistence.DexTaskRecord;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeanchorRecord;
import io.talken.common.util.GSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.exception.*;
import io.talken.dex.shared.exception.auth.AuthenticationException;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import io.talken.dex.shared.service.blockchain.stellar.*;
import io.talken.dex.shared.service.integration.wallet.TalkenWalletService;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.DatePart;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.responses.SubmitTransactionTimeoutResponseException;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class WalletService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(WalletService.class);

	private final TradeWalletService twService;

	private final TalkenWalletService pwService;

	private final MongoTemplate mongoTemplate;

	private final LuniverseNetworkService luniverseNetworkService;

	private final DSLContext dslContext;

	private final TokenMetaApiService tmService;

    private final StellarNetworkService stellarNetworkService;

	private final RedisTemplate<String, Object> redisTemplate;

	private final BigDecimal MINIMUM_LUK_FOR_TRANSFER = BigDecimal.valueOf(2.381);
	private final int LUK_TRANSFER_PENDING_TIME = 5;

	/**
	 * get tradewallet balances
	 *
	 * @param user
	 * @return
	 * @throws TradeWalletCreateFailedException
	 */
	public TradeWalletResult getTradeWalletBalances(User user) throws TradeWalletCreateFailedException {
		TradeWalletInfo tw = twService.getTradeWallet(user);
		TradeWalletResult rtn = new TradeWalletResult();
		Map<String, TradeWalletResult.Balance> balances = new HashMap<>();

		if(tw.isConfirmed()) {
			for(AccountResponse.Balance balance : tw.getAccountResponse().getBalances()) {
				if(!(balance.getAsset() instanceof AssetTypeNative)) {
					TradeWalletResult.Balance b = new TradeWalletResult.Balance();
					b.setBalance(new BigDecimal(balance.getBalance()).stripTrailingZeros());
					b.setBuyLiability(new BigDecimal(balance.getBuyingLiabilities()));
					b.setSellLiability(new BigDecimal(balance.getSellingLiabilities()));
					balances.put(balance.getAssetCode(), b);
				}
			}
		}

		rtn.setActive(tw.isConfirmed());
		rtn.setAddress(tw.getAccountId());
		rtn.setBalances(balances);

		return rtn;
	}


	/**
	 * get trade wallet tx list
	 *
	 * @param address
	 * @param operationType
	 * @param assetCode
	 * @param assetIssuer
	 * @param includeAll
	 * @param direction
	 * @param page
	 * @param offset
	 * @return
	 */
	public List<StellarOpReceipt> getTxList(String address, String operationType, String assetCode, String assetIssuer, boolean includeAll, Sort.Direction direction, int page, int offset) {
		// return empty if address is not given
		if(address == null) return new ArrayList<>();

		List<Criteria> criteriaList = new ArrayList<>();

		criteriaList.add(Criteria.where("involvedAccounts").is(address));

		if(operationType != null) {
			criteriaList.add(Criteria.where("operationType").is(operationType));
		}

		if(assetCode != null) {
			if(assetIssuer != null) {
				criteriaList.add(Criteria.where("involvedAssets").is(assetCode + ":" + assetIssuer));
			} else {
				if(assetCode.equalsIgnoreCase("XLM")) {
					criteriaList.add(Criteria.where("involvedAssets").is("native"));
				}
			}
		}

		if(!includeAll) {
			criteriaList.add(Criteria.where("taskId").ne(null));
		}

		Query qry = new Query()
				.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])))
				.limit(offset)
				.skip(Math.max((page - 1), 0) * offset)
				.with(Sort.by(direction, "timeStamp"));


		return mongoTemplate.find(qry, StellarOpReceipt.class, "stellar_txReceipt");
	}

	/**
	 * prepare luniverse transfer for user private wallet
	 * (refill LUK)
	 * user can request this API once in LUK_TRANSFER_PENDING_TIME seconds
	 *
	 * @param user
	 * @return
	 * @throws IntegrationException
	 * @throws PrivateWalletNotFoundException
	 * @throws TokenMetaNotFoundException
	 * @throws TokenMetaNotManagedException
	 * @throws PendingLastRequestException
	 */
	public synchronized boolean prepareTransferLuk(User user) throws IntegrationException, PrivateWalletNotFoundException, TokenMetaNotFoundException, TokenMetaNotManagedException, PendingLastRequestException {

		final String checkRedisKey = "talken:svc:lukprepare:" + user.getUid();

		Object check = redisTemplate.opsForValue().get(checkRedisKey);

		if(check != null) {
			long lastRequestTime = Long.valueOf(check.toString());
			if(UTCUtil.getNowTimestamp_s() <= lastRequestTime + LUK_TRANSFER_PENDING_TIME) {
				throw new PendingLastRequestException();
			}
		}

		redisTemplate.opsForValue().set(checkRedisKey, UTCUtil.getNowTimestamp_s(), Duration.ofSeconds(LUK_TRANSFER_PENDING_TIME));

		ObjectPair<Boolean, String> address = pwService.getAddress(user.getId(), "LUNIVERSE", "LUK");

		if(!address.first()) throw new PrivateWalletNotFoundException(user.getUid(), "LUNIVERSE", "LUK");
		if(address.second() == null) throw new PrivateWalletNotFoundException(user.getUid(), "LUNIVERSE", "LUK");

		BigDecimal balance = getLukBalance(address.second());

		// need prepare transfer seed LUK
		if(MINIMUM_LUK_FOR_TRANSFER.compareTo(balance) > 0) {
			BigDecimal amount = MINIMUM_LUK_FOR_TRANSFER.subtract(balance);
			BctxRecord bctxRecord = new BctxRecord();
			bctxRecord.setStatus(BctxStatusEnum.QUEUED);
			bctxRecord.setBctxType(BlockChainPlatformEnum.LUNIVERSE);
			bctxRecord.setSymbol("LUK");
			bctxRecord.setAddressFrom(tmService.getManagedInfo("LUK").getDistributorAddress());
			bctxRecord.setAddressTo(address.second());
			bctxRecord.setAmount(amount);
			bctxRecord.setNetfee(BigDecimal.ZERO);

			dslContext.attach(bctxRecord);

			bctxRecord.store();

			return false;
		}

		return true;
	}

	/**
	 * check user private wallet has enough(MINIMUM_LUK_FOR_TRANSFER) LUK for transfer
	 *
	 * @param address
	 * @return
	 */
	public boolean checkTransferLukPrepared(String address) {
        BigDecimal lukBalance = getLukBalance(address);
	    boolean result = MINIMUM_LUK_FOR_TRANSFER.compareTo(lukBalance) <= 0;
        logger.info("checkTransferLukPrepared : {} {} ", MINIMUM_LUK_FOR_TRANSFER, lukBalance);
		return result;
	}

	private BigDecimal getLukBalance(String address) {
		BigInteger balanceRaw = luniverseNetworkService.getBalance(address, null);
		BigDecimal result = (balanceRaw == null) ? BigDecimal.ZERO : Convert.fromWei(balanceRaw.toString(), Convert.Unit.ETHER);
		logger.info("getLukBalance({}) : {}", address, result.stripTrailingZeros().toString());
		return result;
	}

    public ReclaimResult reclaim(User user, ReclaimRequest request)
            throws TokenMetaNotFoundException, TradeWalletCreateFailedException, TokenMetaNotManagedException, SigningException, StellarException, IOException {

        final DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.RECLAIM);
        final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
        final long userId = user.getId();

        BctxLogRecord logRecord = new BctxLogRecord();

        TokenMetaTable.Meta meta;
        meta = tmService.getTokenMeta(request.getAssetCode());
        BctxRecord bctxRecord = new BctxRecord();

        bctxRecord.setBctxType(BlockChainPlatformEnum.STELLAR_TOKEN);
        bctxRecord.setSymbol(meta.getManagedInfo().getAssetCode());
        bctxRecord.setPlatformAux(meta.getManagedInfo().getIssuerAddress());
        bctxRecord.setAddressFrom(tradeWallet.getAccountId());
        bctxRecord.setAddressTo(meta.getManagedInfo().getIssuerAddress());
        bctxRecord.setAmount(request.getAmount());
        bctxRecord.setNetfee(BigDecimal.ZERO);

        // TODO: send use bctx
        final BigDecimal amount = StellarConverter.scale(request.getAmount());
        final KeyPair issuerAccount = tmService.getManagedInfo(request.getAssetCode()).dexIssuerAccount();
        Asset asset = tmService.getAssetType(request.getAssetCode());
        StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();
        // TODO: check convert amount
        sctxBuilder
                .setMemo(dexTaskId.getId())
                .addOperation(
                        new PaymentOperation.Builder(
                                issuerAccount.getAccountId(),
                                asset,
                                StellarConverter.actualToString(amount)
                        ).setSourceAccount(tradeWallet.getAccountId())
                                .build()
                )
                .addSigner(new StellarSignerAccount(twService.extractKeyPair(tradeWallet)));

        long seq;
        String txHash;
        String xdr;
        try(StellarChannelTransaction sctx = sctxBuilder.build()) {
            seq = sctx.getTx().getSequenceNumber();
            txHash = ByteArrayUtil.toHexString(sctx.getTx().hash());
            xdr = sctx.getTx().toEnvelopeXdrBase64();

            SubmitTransactionResponse txResponse = sctx.submit();

            JsonObject requestInfo = new JsonObject();
            requestInfo.addProperty("sequence", seq);
            requestInfo.addProperty("hash", txHash);
            requestInfo.addProperty("envelopeXdr", xdr);

            logRecord.setRequest(GSONWriter.toJsonString(requestInfo));
            logRecord.setBcRefId(txHash);

            if(txResponse.isSuccess()) {
                logRecord.setBcRefId(txResponse.getHash());
                logRecord.setResponse(GSONWriter.toJsonString(txResponse));
                bctxRecord.setStatus(BctxStatusEnum.SUCCESS);
            } else {
                ObjectPair<String, String> resultCodes = StellarConverter.getResultCodesFromExtra(txResponse);
                logRecord.setErrorcode(resultCodes.first());
                logRecord.setErrormessage(resultCodes.second());
                bctxRecord.setStatus(BctxStatusEnum.FAILED);
//                throw StellarException.from(txResponse);
            }
        } catch(AccountRequiresMemoException | SubmitTransactionTimeoutResponseException stex) {
            bctxRecord.setStatus(BctxStatusEnum.FAILED);
        } catch(Exception e) {
            bctxRecord.setStatus(BctxStatusEnum.FAILED);
        }

        dslContext.attach(bctxRecord);
        bctxRecord.store();

        dslContext.attach(logRecord);
        logRecord.store();

        logger.info("{} complete. userId = {}", dexTaskId, userId);

        ReclaimResult result = new ReclaimResult();
        result.setBctx(bctxRecord.into(BCTX).into(Bctx.class));
        result.setBctxLog(logRecord.into(BCTX_LOG).into(BctxLog.class));

        return result;
    }

    public ReclaimResult getReclaimByUser(User user)
            throws TradeWalletCreateFailedException, TaskIntegrityCheckFailedException {

        final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
        ReclaimResult result = new ReclaimResult();

        Record record = dslContext
                .select()
                .from(BCTX)
                .leftJoin(BCTX_LOG).on(BCTX_LOG.BCTX_ID.eq(BCTX.ID))
                .where(BCTX.ADDRESS_FROM.eq(tradeWallet.getAccountId()))
                .fetchAny();

        if (record != null && record.get(BCTX.TX_AUX) != null) {
            DexTaskId dexTaskId = DexTaskId.decode_taskId(record.get(BCTX.TX_AUX));
            if (DexTaskTypeEnum.RECLAIM.equals(dexTaskId.getType())) {
                result.setBctx(record.into(BCTX).into(Bctx.class));
                result.setBctxLog(record.into(BCTX_LOG).into(BctxLog.class));
            }
        }

        return result;
    }
}
