package io.talken.dex.api.controller.mapper;

import com.klaytn.caver.methods.response.Account;
import com.klaytn.caver.methods.response.Transaction;
import com.klaytn.caver.methods.response.TransactionReceipt;
import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.bc.*;
import io.talken.dex.shared.exception.DexException;
import io.talken.dex.shared.service.blockchain.klaytn.Kip7ContractInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.model.TransferArray;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.wallet.model.TransactionResult;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * The type Block chain info controller.
 */
@RestController
public class BlockChainInfoController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(BlockChainInfoController.class);

	@Autowired
	private LuniverseInfoService luniverseInfoService;

	@Autowired
	private EthereumInfoService ethereumInfoService;

    @Autowired
    private KlaytnInfoService klaytnInfoService;

    @Autowired
	private BscInfoService bscInfoService;

	@Autowired
	private HecoInfoService hecoInfoService;

    /**
     * get luniverse gas price and limit
     *
     * @return dex response
     * @throws DexException the dex exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_LUNIVERSE_GASPRICE, method = RequestMethod.GET)
	public DexResponse<LuniverseGasPriceResult> luniverseGasPrice() throws DexException {
		return DexResponse.buildResponse(luniverseInfoService.getGasPriceAndLimit());
	}

    /**
     * get luniverse tx list
     *
     * @param address         the address
     * @param contractaddress the contractaddress
     * @param sort            the sort
     * @param page            the page
     * @param offset          the offset
     * @return luniverse tx list similar to etherscan.io
     * @throws DexException the dex exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_LUNIVERSE_TXLIST, method = RequestMethod.GET)
	public DexResponse<LuniverseTxListResult> luniverseTxList(
			@RequestParam("address")
					String address,
			@RequestParam(value = "contractaddress", required = false)
					String contractaddress,
			@RequestParam(value = "sort", defaultValue = "desc")
					String sort,
			@RequestParam(value = "page", defaultValue = "1")
					Integer page,
			@RequestParam(value = "offset", defaultValue = "10")
					Integer offset
	) throws DexException {
		Sort.Direction direction;
		if(sort == null || !sort.equalsIgnoreCase("asc")) direction = Sort.Direction.DESC;
		else direction = Sort.Direction.ASC;
		if(page == null) page = 1;
		if(offset == null) offset = 10;

		return DexResponse.buildResponse(luniverseInfoService.getTxList(address, contractaddress, direction, page, offset));
	}

    /**
     * get eth balance
     *
     * @param postBody the post body
     * @return eth balance
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETETHBALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getEthBalance(@RequestBody EthBalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(ethereumInfoService.getEthBalance(postBody.getAddress()));
	}

    /**
     * get erc20 balance
     *
     * @param postBody the post body
     * @return erc 20 balance
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETERC20BALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getErc20Balance(@RequestBody Erc20BalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(ethereumInfoService.getErc20Balance(postBody.getContract(), postBody.getAddress()));
	}

    /**
     * Gets pending transaction tx list.
     *
     * @param request the request
     * @return the pending transaction tx list
     * @throws Exception the exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETPENDING_TXLIST, method = RequestMethod.POST)
    public DexResponse<PendingTxListResult> getPendingTransactionTxList(@RequestBody PendingTxListRequest request) throws Exception {
        return DexResponse.buildResponse(ethereumInfoService.getPendingTransactionTxList(request));
    }

    /**
     * Gets transaction count.
     *
     * @param address the address
     * @return the transaction count
     * @throws Exception the exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETTRANSACTIONCOUNT, method = RequestMethod.GET)
    public DexResponse<Map<String, BigInteger>> getTransactionCount(@RequestParam("address") String address) throws Exception {
	    if (address != null) {
	        return DexResponse.buildResponse(ethereumInfoService.getTransactionCount(address));
        } else {
            return DexResponse.buildResponse(new HashMap<>());
        }
    }

    /**
     * Gets transaction.
     *
     * @param txHash the tx hash
     * @return the transaction
     * @throws Exception the exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_BY_HASH, method = RequestMethod.GET)
    public DexResponse<EthTransactionResult> getTransaction(@RequestParam("txHash") String txHash) throws Exception {
        return DexResponse.buildResponse(ethereumInfoService.getEthTransaction(txHash));
    }

    /**
     * Gets transaction receipt.
     *
     * @param txHash the tx hash
     * @return the transaction receipt
     * @throws Exception the exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_RECEIPT_BY_HASH, method = RequestMethod.GET)
    public DexResponse<EthTransactionReceiptResult> getTransactionReceipt(@RequestParam("txHash") String txHash) throws Exception {
        if (txHash != null) {
            return DexResponse.buildResponse(ethereumInfoService.getEthTransactionReceipt(txHash));
        } else {
            return DexResponse.buildResponse(null);
        }
    }

    /**
     * get klay account
     *
     * @param postBody the post body
     * @return klay account
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GETACCOUNT, method = RequestMethod.POST)
    public DexResponse<Account.AccountData> getKlayAccount(@RequestBody EthBalanceRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(klaytnInfoService.getAccount(postBody.getAddress()));
    }

    /**
     * get klay balance
     *
     * @param postBody the post body
     * @return klay balance
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GETBALANCE, method = RequestMethod.POST)
    public DexResponse<BigInteger> getKlayBalance(@RequestBody EthBalanceRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(klaytnInfoService.getBalance(postBody.getAddress()));
    }

    /**
     * get klay balance
     *
     * @param postBody the post body
     * @return klay kip 7 balance
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GETKIP7BALANCE, method = RequestMethod.POST)
    public DexResponse<BigInteger> getKlayKIP7Balance(@RequestBody Erc20BalanceRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(klaytnInfoService.getKip7Balance(postBody.getContract(), postBody.getAddress()));
    }

    /**
     * Gets klay contract info.
     *
     * @param postBody the post body
     * @return the klay contract info
     * @throws TalkenException the talken exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GETKIP7INFO, method = RequestMethod.POST)
    public DexResponse<Kip7ContractInfoService.Kip7ContractInfo> getKlayContractInfo(@RequestBody Erc20BalanceRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(klaytnInfoService.getContract(postBody.getContract(), postBody.getAddress()));
    }


    /**
     * get klay gasPrice
     *
     * @return klay gas price
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GASPRICE, method = RequestMethod.GET)
    public DexResponse<BigInteger> getKlayGasPrice() throws TalkenException {
        return DexResponse.buildResponse(klaytnInfoService.getGasPrice());
    }

    /**
     * get klay transaction
     *
     * @param txHash the tx hash
     * @return klay transaction
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_BY_HASH, method = RequestMethod.GET)
    public DexResponse<Transaction.TransactionData> getKlayTransaction(@RequestParam("txHash") String txHash) throws TalkenException {
        if (txHash != null) {
            return DexResponse.buildResponse(klaytnInfoService.getTransactionByHash(txHash));
        } else {
            return DexResponse.buildResponse(null);
        }
    }

    /**
     * get klay transactionReceipt
     *
     * @param txHash the tx hash
     * @return klay transaction receipt
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_RECEIPT_BY_HASH, method = RequestMethod.GET)
    public DexResponse<TransactionReceipt.TransactionReceiptData> getKlayTransactionReceipt(@RequestParam("txHash") String txHash) throws TalkenException {
        if (txHash != null) {
            return DexResponse.buildResponse(klaytnInfoService.getTransactionReceiptByHash(txHash));
        } else {
            return DexResponse.buildResponse(null);
        }
    }

    /**
     * get klay transactionList
     *
     * @param postBody the post body
     * @return klay transaction receipt
     * @throws TalkenException the talken exception
     */
    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_LIST, method = RequestMethod.POST)
    public DexResponse<TransferArray> getKlayTransactionReceipt(@RequestBody KlayTransactionListRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(klaytnInfoService.getTransactionList(postBody));
    }

    /**
     * get Bsc balance
     *
     * @param postBody the post body
     * @return bsc balance
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GETBALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getBscBalance(@RequestBody EthBalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(bscInfoService.getBscBalance(postBody.getAddress()));
	}

    /**
     * get bep20 balance
     *
     * @param postBody the post body
     * @return bep 20 balance
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GETBEP20BALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getBep20Balance(@RequestBody Erc20BalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(bscInfoService.getBep20Balance(postBody.getContract(), postBody.getAddress()));
	}

    /**
     * get Bsc gasPrice
     *
     * @return bsc gas price and limit
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GASPRICE, method = RequestMethod.GET)
	public DexResponse<BscGasPriceResult> getBscGasPriceAndLimit() throws TalkenException {
		return DexResponse.buildResponse(bscInfoService.getGasPriceAndLimit());
	}

    /**
     * Gets bep 20 gas price and limit.
     *
     * @return the bep 20 gas price and limit
     * @throws TalkenException the talken exception
     */
//    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_BEP20GASPRICE, method = RequestMethod.GET)
	public DexResponse<BscGasPriceResult> getBep20GasPriceAndLimit() throws TalkenException {
		return DexResponse.buildResponse(bscInfoService.getBep20GasPriceAndLimit());
	}

    /**
     * get bsc transaction by hash
     *
     * @param txHash the tx hash
     * @return bsc transaction
     * @throws Exception the exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GET_TRANSACTION_BY_HASH, method = RequestMethod.GET)
	public DexResponse<EthTransactionResult> getBscTransaction(@RequestParam("txHash") String txHash) throws Exception {
		return DexResponse.buildResponse(bscInfoService.getBscTransaction(txHash));
	}

    /**
     * get bsc transaction receipt by hash
     *
     * @param txHash the tx hash
     * @return bsc transaction receipt
     * @throws Exception the exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GET_TRANSACTION_RECEIPT_BY_HASH, method = RequestMethod.GET)
	public DexResponse<EthTransactionReceiptResult> getBscTransactionReceipt(@RequestParam("txHash") String txHash) throws Exception {
		if (txHash != null) {
			return DexResponse.buildResponse(bscInfoService.getBscTransactionReceipt(txHash));
		} else {
			return DexResponse.buildResponse(null);
		}
	}

    /**
     * get Heco balance
     *
     * @param postBody the post body
     * @return heco balance
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_GETBALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getHecoBalance(@RequestBody EthBalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(hecoInfoService.getHecoBalance(postBody.getAddress()));
	}

    /**
     * get Heco gasPrice
     *
     * @return heco gas price and limit
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_GASPRICE, method = RequestMethod.GET)
	public DexResponse<HecoGasPriceResult> getHecoGasPriceAndLimit() throws TalkenException {
		return DexResponse.buildResponse(hecoInfoService.getGasPriceAndLimit());
	}

    /**
     * Gets hrc 20 gas price and limit.
     *
     * @return the hrc 20 gas price and limit
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_HRC20GASPRICE, method = RequestMethod.GET)
	public DexResponse<HecoGasPriceResult> getHrc20GasPriceAndLimit() throws TalkenException {
		return DexResponse.buildResponse(hecoInfoService.getHrc20GasPriceAndLimit());
	}

    /**
     * get heco transaction by hash
     *
     * @param txHash the tx hash
     * @return heco transaction
     * @throws Exception the exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_GET_TRANSACTION_BY_HASH, method = RequestMethod.GET)
	public DexResponse<EthTransactionResult> getHecoTransaction(@RequestParam("txHash") String txHash) throws Exception {
		return DexResponse.buildResponse(hecoInfoService.getHecoTransaction(txHash));
	}

    /**
     * get heco transaction receipt by hash
     *
     * @param txHash the tx hash
     * @return heco transaction receipt
     * @throws Exception the exception
     */
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_GET_TRANSACTION_RECEIPT_BY_HASH, method = RequestMethod.GET)
	public DexResponse<EthTransactionReceiptResult> getHecoTransactionReceipt(@RequestParam("txHash") String txHash) throws Exception {
		if (txHash != null) {
			return DexResponse.buildResponse(hecoInfoService.getHecoTransactionReceipt(txHash));
		} else {
			return DexResponse.buildResponse(null);
		}
	}

    /**
     * get hrc20 balance
     *
     * @param postBody the post body
     * @return hrc 20 balance
     * @throws TalkenException the talken exception
     */
    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_GETHRC20BALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getHrc20Balance(@RequestBody Erc20BalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(hecoInfoService.getHrc20Balance(postBody.getContract(), postBody.getAddress()));
	}

    //    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_SEND, method = RequestMethod.POST)
    public DexResponse<TransactionResult> send(@RequestBody KlaySendRequest postBody) throws Exception {
        DTOValidator.validate(postBody);
        TransactionResult result = klaytnInfoService.send(postBody.getSymbol(), postBody.getTo(), postBody.getAmount());
        return DexResponse.buildResponse(result);
    }

    //    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_SEND_CONTRACT, method = RequestMethod.POST)
    public DexResponse<TransactionReceipt.TransactionReceiptData> sendContract(@RequestBody KlaySendRequest postBody) throws Exception {
        DTOValidator.validate(postBody);
        TransactionReceipt.TransactionReceiptData receipt = klaytnInfoService.sendContract(postBody.getSymbol(), postBody.getContract(), postBody.getTo(), postBody.getAmount());
        return DexResponse.buildResponse(receipt);
    }
}