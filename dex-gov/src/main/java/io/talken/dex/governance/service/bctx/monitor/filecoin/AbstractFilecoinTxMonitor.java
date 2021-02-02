package io.talken.dex.governance.service.bctx.monitor.filecoin;

import io.talken.common.RunningProfile;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.ethereum.StandardERC20ContractFunctions;
import io.talken.dex.shared.service.blockchain.filecoin.*;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Event;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public abstract class AbstractFilecoinTxMonitor extends TxMonitor<FilecoinMessage.Block, FilecoinMessage.SecpkMessage, FilecoinMessage.SecpkMessage> {
	private final PrefixedLogger logger;

	private static final int MAXIMUM_LOOP = 10; // get 1000 blocks per loop, for reduce crawl load.
	private final String networkName;
	private static final BigInteger CONFIRM_BLOCK_COUNT = BigInteger.valueOf(2);

	private static final int ETA_BLOCKS = 100;
	private long[] receiptsPerBlock = new long[ETA_BLOCKS];
	private long[] takesPerBlock = new long[ETA_BLOCKS];

	public AbstractFilecoinTxMonitor(PrefixedLogger logger, String networkName) {
		this.logger = logger;
		this.networkName = networkName;
		for(int i = 0; i < ETA_BLOCKS; i++) {
			receiptsPerBlock[i] = 0;
			takesPerBlock[i] = 0;
		}
	}

	abstract protected BigInteger getServiceStatusLastBlock();

	abstract protected void saveServiceStatusLastBlock(BigInteger blockNumber, LocalDateTime timestamp);

	abstract protected void saveReceiptDocuments(List<FilecoinMessage.SecpkMessage> documents);

	protected void crawlBlocks(FilecoinRpcClient client, int collectionThreadNum) {
		BigInteger latestBlockNumber = null;
		BigInteger targetBlockNumber = null;
		BigInteger cursor = null;

		try {
			Map<String, Object> result = client.getChainHead();
			latestBlockNumber = BigInteger.valueOf(Long.parseLong(result.get("Height").toString()));

			if(latestBlockNumber == null || latestBlockNumber.compareTo(CONFIRM_BLOCK_COUNT) <= 0) {
				logger.warn("{} latest block returned {}, cancel monitoring", networkName, latestBlockNumber);
				return;
			}

			// substract latest-CONFIRM_BLOCK_COUNT for block confirmation
			targetBlockNumber = (RunningProfile.isProduction()) ? latestBlockNumber.subtract(CONFIRM_BLOCK_COUNT) : latestBlockNumber;

			Optional<BigInteger> opt_lastBlock = Optional.ofNullable(getServiceStatusLastBlock());

			if(opt_lastBlock.isPresent()) {
				cursor = opt_lastBlock.get();
			} else {
				logger.info("{} block collection not found, collect last 10 blocks for initial data.", networkName);
				cursor = targetBlockNumber.subtract(new BigInteger("10")); // initially collect 10 blocks
			}

			// stop if targetBlockNumber is not higher then cursor
			if(targetBlockNumber.compareTo(cursor) <= 0) return;
		} catch(Exception ex) {
			logger.exception(ex, "Cannot determine {} block cursor.", networkName);
			return;
		}

		try {
			for(int i = 0; i < MAXIMUM_LOOP && targetBlockNumber.compareTo(cursor) > 0; i++) {
				if(DexGovStatus.isStopped) break;

				cursor = cursor.add(BigInteger.ONE);

				// for ETA
				final long started = System.currentTimeMillis();

				// test code
				TipSet tipSet = client.getChainGetTipSetByHeight(cursor);
				List<FilecoinMessage.SecpkMessage> txs = new ArrayList<>();
				for(Cid cid: tipSet.getCids()){
					BlockMessages blsMsgs = client.getChainGetBlockMessages(cid);
					if (blsMsgs.getSecpkMessages().length > 0) {
						for (FilecoinMessage.SecpkMessage secpkMsg: blsMsgs.getSecpkMessages()) {
							if ("0".equals(secpkMsg.getMessage().getMethod())) {
								txs.add(secpkMsg);
							}
						}
					}
				}

				final long startCollect = System.currentTimeMillis();
				final long collectTakes = System.currentTimeMillis() - startCollect;
//                ObjectMapper mapper = new ObjectMapper();
//                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

				// call handlerStacks
//				callBlockHandlerStack(null);

//				이해 안됨!
//				for(ObjectPair<TransactionReceipt, List<EthereumTransferReceipt>> txReceipt : allReceipts) {
//					callTxHandlerStack(block, txReceipt.first());
//					for(EthereumTransferReceipt rcpt : txReceipt.second()) {
//						callReceiptHandlerStack(block, txReceipt.first(), rcpt);
//					}
//				}

				for(FilecoinMessage.SecpkMessage msg : txs) {
					callReceiptHandlerStack(null, null, msg);
				}

				saveServiceStatusLastBlock(BigInteger.valueOf(Long.parseLong(tipSet.getHeight())), UTCUtil.ts2ldt(tipSet.getBlocks().get(0).getTimestamp().longValue()));
				saveReceiptDocuments(txs);

				// for ETA
				long takes = System.currentTimeMillis() - started;

				int eta_slot = (int) (cursor.longValueExact() % ETA_BLOCKS);
				takesPerBlock[eta_slot] = takes;
				receiptsPerBlock[eta_slot] = txs.size();
				long blocksToGo = targetBlockNumber.subtract(cursor).longValueExact();
				String eta = "";

				try {
					long receiptsPerBlockSum = Arrays.stream(receiptsPerBlock).sum();
					long takesPerBlockSum = Arrays.stream(takesPerBlock).sum();
					// ((receiptsPerBlockSum / ETA_BLOCKS) * blocksToGo) = receipts to go
					// (takesPerBlockSum / receiptsPerBlockSum) = takes per receipt
					// etams = receipts to go * takes per receipts
					long etams = ((receiptsPerBlockSum / ETA_BLOCKS) * blocksToGo) * (takesPerBlockSum / receiptsPerBlockSum);
					if(etams > 0) {
						eta = ", catchup ETA : " + blocksToGo + " blocks in " + Duration.ofMillis(etams).toString();
					}
				} catch(Exception ex) {
					// do nothing
				}

				// log process if receipts processed
				if((txs.size() > 0) || (cursor.longValueExact() % 30 == 0))
					logger.info("{} : BLOCKNUMBER = {}/{}, RECEIPTS = {} ({} ms, collect {} ms){}", networkName, cursor, latestBlockNumber, txs.size(), takes, collectTakes, eta);
			}
		} catch(Exception ex) {
			logger.exception(ex, "Exception while processing {} block {}", networkName, cursor);
		}
	}

	private static final Event transferEvent = StandardERC20ContractFunctions.transferEvent();
	private static final String encodedTransferEventTopic = EventEncoder.encode(transferEvent);

//	private List<EthereumTransferReceipt> getTransfers(Web3j web3j, EthBlock.Block block, Transaction tx, TransactionReceipt receipt) {
//		List<EthereumTransferReceipt> rtn = new ArrayList<>();
//
//		EthereumTransferReceipt common_txr = new EthereumTransferReceipt();
//
//		common_txr.setTimeStamp(block.getTimestamp());
//		common_txr.setNonce(tx.getNonce().toString());
//		common_txr.setHash(tx.getHash());
//		common_txr.setBlockNumber(block.getNumber().toString());
//		common_txr.setBlockHash(block.getHash());
//		common_txr.setIsError(receipt.isStatusOK() ? "0" : "1");
//		common_txr.setTxreceipt_status((receipt.getStatus() != null) ? Numeric.decodeQuantity(receipt.getStatus()).toString() : "");
//		common_txr.setTransactionIndex(receipt.getTransactionIndex().toString());
//		common_txr.setGas(tx.getGas().toString());
//		common_txr.setGasPrice(tx.getGasPrice().toString());
//		common_txr.setGasUsed(receipt.getGasUsed().toString());
//		common_txr.setCumulativeGasUsed(receipt.getCumulativeGasUsed().toString());
//
//		// extract erc20 transfers from log
//		if(receipt.getLogs() != null && receipt.getTo() != null) {
//			for(Log log : receipt.getLogs()) {
//				EthereumTransferReceipt txr = null;
//				try {
//					// if log contains erc20 transfer event
//					if(log.getTopics() != null && log.getTopics().size() > 0 && log.getTopics().get(0).equals(encodedTransferEventTopic)) {
//						// erc20 standard topics : indexed from, to and non-indexed value
//						if(log.getTopics().size() == 3) {
//							List<Type> values = FunctionReturnDecoder.decode(log.getData(), transferEvent.getNonIndexedParameters());
//							if(values != null && values.size() == 1) { // successfully decoded
//								txr = new EthereumTransferReceipt();
//								BeanCopier.copy(common_txr, txr);
//								txr.setContractAddress(receipt.getTo());
//								txr.setFrom(new Address(log.getTopics().get(1)).toString());
//								txr.setTo(new Address(log.getTopics().get(2)).toString());
//								txr.setValue(((Uint256) values.get(0)).getValue().toString());
//								txr.setInput("deprecated"); // etherscan mockup
//							}
//						}
//						// erc20 non-standard topics : non-indexed from,to,value
//						else if(log.getTopics().size() == 1) {
//							List<Type> values = FunctionReturnDecoder.decode(log.getData(), transferEvent.getParameters());
//							if(values != null && values.size() == 3) { // successfully decoded
//								txr = new EthereumTransferReceipt();
//								BeanCopier.copy(common_txr, txr);
//								txr.setContractAddress(receipt.getTo());
//								txr.setFrom(((Address) values.get(0)).toString());
//								txr.setTo(((Address) values.get(1)).toString());
//								txr.setValue(((Uint256) values.get(2)).getValue().toString());
//								txr.setInput("deprecated"); // etherscan mockup
//							}
//						}
//
//						if(txr != null) {
//							try {
//								Erc20ContractInfoService.Erc20ContractInfo erc20ContractInfo = erc20ContractInfoService.getErc20ContractInfo(web3j, receipt.getTo());
//								txr.setTokenName(erc20ContractInfo.getName());
//								txr.setTokenSymbol(erc20ContractInfo.getSymbol());
//								txr.setTokenDecimal(erc20ContractInfo.getDecimals().toString());
//								logger.trace("[I] {}({}) {} -> {} {} ({} {})", txr.getContractAddress(), tx.getHash(), txr.getFrom(), txr.getTo(), txr.getValue(), erc20ContractInfo.getSymbol(), erc20ContractInfo.getDecimals());
//							} catch(Exception ex) {
//								logger.exception(ex, "Cannot extract erc20ContractInfo from {}", tx.getHash());
//							}
//						}
//					}
//
//					// add txr if not null
//					if(txr != null) rtn.add(txr);
//				} catch(Exception ex) {
//					logger.exception(ex, "Cannot decode txReceipt ABI : {}", tx.getHash());
//				}
//			}
//		}
//
//		// native (eth transfers)
//		if(tx.getValue().compareTo(BigInteger.ZERO) != 0) {
//			EthereumTransferReceipt txr = new EthereumTransferReceipt();
//			BeanCopier.copy(common_txr, txr);
//			txr.setContractAddress(receipt.getContractAddress());
//			txr.setFrom(receipt.getFrom());
//			txr.setTo(receipt.getTo());
//			txr.setValue(tx.getValue().toString());
//			txr.setInput(tx.getInput());
//			rtn.add(txr);
//		}
//
//		return rtn;
//	}

	@Override
	protected TxReceipt toTxMonitorReceipt(FilecoinMessage.SecpkMessage tx) {
//		if(tx.isStatusOK()) {
			return TxReceipt.ofSuccessful(tx.getCid().getRoot(), tx);
//		} else {
//			return TxReceipt.ofFailed(tx.getTransactionHash(), tx);
//		}
	}
}
