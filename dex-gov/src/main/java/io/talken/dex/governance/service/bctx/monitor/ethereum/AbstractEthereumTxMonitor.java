package io.talken.dex.governance.service.bctx.monitor.ethereum;

import io.talken.common.util.BeanCopier;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.ethereum.Erc20ContractInfoService;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumTxReceipt;
import io.talken.dex.shared.service.blockchain.ethereum.StandardERC20ContractFunctions;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractEthereumTxMonitor extends TxMonitor<EthBlock.Block, TransactionReceipt, EthereumTxReceipt> {
	private final PrefixedLogger logger;

	@Autowired
	private Erc20ContractInfoService erc20ContractInfoService;

	private static final int MAXIMUM_LOOP = 1000; // get 1000 blocks per loop, for reduce crawl load.
	private final String networkName;

	public AbstractEthereumTxMonitor(PrefixedLogger logger, String networkName) {
		this.logger = logger;
		this.networkName = networkName;
	}

	abstract protected BigInteger getServiceStatusLastBlock();

	abstract protected void saveServiceStatusLastBlock(BigInteger blockNumber, LocalDateTime timestamp);

	abstract protected void saveReceiptDocuments(List<EthereumTxReceipt> documents);

	abstract protected boolean checkTxHashNeedsHandling(String txHash);

	private BigInteger getCursor(Web3j web3j) throws Exception {
		Optional<BigInteger> opt_lastBlock = Optional.ofNullable(getServiceStatusLastBlock());

		if(opt_lastBlock.isPresent()) {
			return opt_lastBlock.get();
		} else {
			logger.info("{} block collection not found, collect last 10 blocks for initial data.", networkName);
			return getLatestBlockNumber(web3j).subtract(new BigInteger("10")); // initially collect 100 blocks
		}
	}

	private BigInteger getLatestBlockNumber(Web3j web3j) throws Exception {
		return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
	}

	protected void crawlBlocks(Web3j web3j) {
		BigInteger cursor;
		try {
			cursor = getCursor(web3j);
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get block cursor from mongodb.");
			return;
		}

		try {
			BigInteger latestBlockNumber = getLatestBlockNumber(web3j);

			if(latestBlockNumber.compareTo(cursor) > 0) { // higher block found
				logger.trace("{} latest block {} found, current cursor = {}", networkName, latestBlockNumber, cursor);

				for(int i = 0; i < MAXIMUM_LOOP && latestBlockNumber.compareTo(cursor) > 0; i++) {
					BigInteger nextCursor = cursor.add(BigInteger.ONE);

					// get next block of cursor
					EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(nextCursor), true).send().getBlock();

					if(block != null) {
						logger.verbose("{} block {} contains {} tx.", networkName, block.getNumber(), block.getTransactions().size());

						callBlockHandlerStack(block);

						List<EthereumTxReceipt> txReceiptDocuments = new ArrayList<>();

						if(block.getTransactions().size() > 0) {
							for(EthBlock.TransactionResult tx : block.getTransactions()) {

								Transaction transaction = null;

								if(tx instanceof EthBlock.TransactionHash) {
									transaction = web3j.ethGetTransactionByHash((String) tx.get()).send().getTransaction().orElse(null);
								} else if(tx instanceof EthBlock.TransactionObject) {
									transaction = (Transaction) tx.get();
								}

								if(transaction != null) {
									if(checkTxHashNeedsHandling(transaction.getHash())) {
										Optional<TransactionReceipt> opt_receipt = web3j.ethGetTransactionReceipt(transaction.getHash()).send().getTransactionReceipt();
										if(opt_receipt.isPresent()) {
											callTxHandlerStack(opt_receipt.get());

											List<EthereumTxReceipt> transfers = getTransfers(web3j, block, transaction, opt_receipt.get());
											for(EthereumTxReceipt transfer : transfers) {
												txReceiptDocuments.add(transfer);
												callReceiptHandlerStack(transfer);
											}
										} else {
											logger.error("Cannot get tx receipt from network, cancel monitoring");
											break;
										}
									}
								} else {
									logger.error("Cannot extract tx from block response, cancel monitoring");
									break;
								}
							}
						}

						saveServiceStatusLastBlock(block.getNumber(), UTCUtil.ts2ldt(block.getTimestamp().longValue()));
						saveReceiptDocuments(txReceiptDocuments);

						cursor = block.getNumber();
					} else {
						logger.error("GetBlock {} returned null, cancel monitoring", nextCursor);
						break;
					}
				}
			}
		} catch(Exception ex) {
			logger.exception(ex);
		}
	}

	private static final Event transferEvent = StandardERC20ContractFunctions.transferEvent();
	private static final String encodedTransferEventTopic = EventEncoder.encode(transferEvent);

	private List<EthereumTxReceipt> getTransfers(Web3j web3j, EthBlock.Block block, Transaction tx, TransactionReceipt receipt) {
		List<EthereumTxReceipt> rtn = new ArrayList<>();

		EthereumTxReceipt common_txr = new EthereumTxReceipt();

		common_txr.setTimeStamp(block.getTimestamp());
		common_txr.setNonce(tx.getNonce().toString());
		common_txr.setHash(tx.getHash());
		common_txr.setBlockNumber(block.getNumber().toString());
		common_txr.setBlockHash(block.getHash());
		common_txr.setIsError(receipt.isStatusOK() ? "0" : "1");
		common_txr.setTxreceipt_status((receipt.getStatus() != null) ? Numeric.decodeQuantity(receipt.getStatus()).toString() : "");
		common_txr.setTransactionIndex(receipt.getTransactionIndex().toString());
		common_txr.setGas(tx.getGas().toString());
		common_txr.setGasPrice(tx.getGasPrice().toString());
		common_txr.setGasUsed(receipt.getGasUsed().toString());
		common_txr.setCumulativeGasUsed(receipt.getCumulativeGasUsed().toString());

		if(receipt.getLogs() != null) {
			for(Log log : receipt.getLogs()) {
				try {
					// log contains erc20 transfer
					// log topic has addresses
					if(log.getTopics() != null && log.getTopics().size() == 3 && log.getTopics().get(0).equals(encodedTransferEventTopic)) {
						List<Type> values = FunctionReturnDecoder.decode(log.getData(), transferEvent.getNonIndexedParameters());
						if(values != null && values.size() == 3) { // successfully decoded
							EthereumTxReceipt txr = new EthereumTxReceipt();
							BeanCopier.copy(common_txr, txr);
							txr.setContractAddress(receipt.getTo());
							txr.setFrom(new Address(log.getTopics().get(1)).toString());
							txr.setTo(new Address(log.getTopics().get(2)).toString());
							txr.setValue(((Uint256) values.get(0)).getValue().toString());
							txr.setInput("deprecated"); // like etherscan
							Erc20ContractInfoService.Erc20ContractInfo erc20ContractInfo = erc20ContractInfoService.getErc20ContractInfo(web3j, receipt.getTo());
							if(erc20ContractInfo != null) {
								txr.setTokenName(erc20ContractInfo.getName());
								txr.setTokenSymbol(erc20ContractInfo.getSymbol());
								txr.setTokenDecimal(erc20ContractInfo.getDecimals().toString());
							} else {
								logger.debug("Cannot get ERC20 contract info for {}, txReceipt is will not stored properly", receipt.getTo());
							}
							rtn.add(txr);
						}
					}
					// log contains erc20 transfer
					// log data contains addresses and value
					else if(log.getTopics() != null && log.getTopics().size() == 1 && log.getTopics().get(0).equals(encodedTransferEventTopic)) {
						List<Type> values = FunctionReturnDecoder.decode(log.getData(), transferEvent.getParameters());
						if(values != null && values.size() == 3) { // successfully decoded
							EthereumTxReceipt txr = new EthereumTxReceipt();
							BeanCopier.copy(common_txr, txr);
							txr.setContractAddress(receipt.getTo());
							txr.setFrom(((Address) values.get(0)).toString());
							txr.setTo(((Address) values.get(1)).toString());
							txr.setValue(((Uint256) values.get(2)).getValue().toString());
							txr.setInput("deprecated"); // like etherscan
							Erc20ContractInfoService.Erc20ContractInfo erc20ContractInfo = erc20ContractInfoService.getErc20ContractInfo(web3j, receipt.getTo());
							if(erc20ContractInfo != null) {
								txr.setTokenName(erc20ContractInfo.getName());
								txr.setTokenSymbol(erc20ContractInfo.getSymbol());
								txr.setTokenDecimal(erc20ContractInfo.getDecimals().toString());
							} else {
								logger.debug("Cannot get ERC20 contract info for {}, txReceipt is will not stored properly", receipt.getTo());
							}
							rtn.add(txr);
						}
					}

				} catch(Exception ex) {
					logger.exception(ex, "Cannot decode ABI from txReceipt");
				}
			}
		}

		if(tx.getValue().compareTo(BigInteger.ZERO) != 0) {
			EthereumTxReceipt txr = new EthereumTxReceipt();
			BeanCopier.copy(common_txr, txr);
			txr.setContractAddress(receipt.getContractAddress());
			txr.setFrom(receipt.getFrom());
			txr.setTo(receipt.getTo());
			txr.setValue(tx.getValue().toString());
			txr.setInput(tx.getInput());
			rtn.add(txr);
		}

		return rtn;
	}

	@Override
	protected TxReceipt toTxMonitorReceipt(TransactionReceipt tx) {
		if(tx.isStatusOK()) {
			return TxReceipt.ofSuccessful(tx.getTransactionHash(), tx);
		} else {
			return TxReceipt.ofFailed(tx.getTransactionHash(), tx);
		}
	}
}
