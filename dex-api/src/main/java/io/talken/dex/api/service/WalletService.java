package io.talken.dex.api.service;

import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.api.controller.dto.TradeWalletResult;
import io.talken.dex.shared.exception.PendingLastRequestException;
import io.talken.dex.shared.exception.PrivateWalletNotFoundException;
import io.talken.dex.shared.exception.TradeWalletCreateFailedException;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import io.talken.dex.shared.service.integration.wallet.TalkenWalletService;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.responses.AccountResponse;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Scope("singleton")
public class WalletService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(WalletService.class);

	@Autowired
	private TradeWalletService twService;

	@Autowired
	private TalkenWalletService pwService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private LuniverseNetworkService luniverseNetworkService;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private TokenMetaService tmService;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	private final BigDecimal MINIMUM_LUK_FOR_TRANSFER = BigDecimal.valueOf(2);
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


		return mongoTemplate.find(qry, StellarOpReceipt.class, "stellar_opReceipt");
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

		if(check == null) {
			throw new PendingLastRequestException();
		} else {
			long lastRequestTime = Long.valueOf(check.toString());
			if(UTCUtil.getNowTimestamp_s() <= lastRequestTime + LUK_TRANSFER_PENDING_TIME) {
				throw new PendingLastRequestException();
			}
		}

		redisTemplate.opsForValue().set(checkRedisKey, UTCUtil.getNowTimestamp_s(), Duration.ofSeconds(LUK_TRANSFER_PENDING_TIME));

		ObjectPair<Boolean, String> address = pwService.getAddress(user.getId(), "luk", "LUK");

		if(!address.first()) throw new PrivateWalletNotFoundException(user.getUid(), "luk", "LUK");

		BigInteger balanceRaw = luniverseNetworkService.getBalance(address.second(), null);

		BigDecimal balance = Convert.fromWei(balanceRaw.toString(), Convert.Unit.ETHER);

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
		BigInteger balanceRaw = luniverseNetworkService.getBalance(address, null);

		BigDecimal balance = Convert.fromWei(balanceRaw.toString(), Convert.Unit.ETHER);

		return MINIMUM_LUK_FOR_TRANSFER.compareTo(balance) <= 0;
	}
}
