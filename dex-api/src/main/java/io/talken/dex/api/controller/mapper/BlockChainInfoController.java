package io.talken.dex.api.controller.mapper;

import com.klaytn.caver.methods.response.Account;
import com.klaytn.caver.methods.response.Transaction;
import com.klaytn.caver.methods.response.TransactionReceipt;
import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.bc.*;
import io.talken.dex.shared.exception.DexException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import xyz.groundx.caver_ext_kas.rest_client.io.swagger.client.api.tokenhistory.model.TransferArray;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

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

	@Autowired
	private AuthInfo authInfo;

	/**
	 * get luniverse gas price and limit
	 *
	 * @return
	 * @throws DexException
	 */
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_LUNIVERSE_GASPRICE, method = RequestMethod.GET)
	public DexResponse<LuniverseGasPriceResult> luniverseGasPrice() throws DexException {
		return DexResponse.buildResponse(luniverseInfoService.getGasPriceAndLimit());
	}

	/**
	 * get luniverse tx list
	 *
	 * @param address
	 * @param contractaddress
	 * @param sort
	 * @param page
	 * @param offset
	 * @return luniverse tx list similar to etherscan.io
	 * @throws DexException
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
	 * @param postBody
	 * @return
	 * @throws TalkenException
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
	 * @param postBody
	 * @return
	 * @throws TalkenException
	 */
	@AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETERC20BALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getErc20Balance(@RequestBody Erc20BalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(ethereumInfoService.getErc20Balance(postBody.getContract(), postBody.getAddress()));
	}

    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETPENDING_TXLIST, method = RequestMethod.POST)
    public DexResponse<PendingTxListResult> getPendingTransactionTxList(@RequestBody PendingTxListRequest request) throws Exception {
        return DexResponse.buildResponse(ethereumInfoService.getPendingTransactionTxList(request));
    }

    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GETTRANSACTIONCOUNT, method = RequestMethod.GET)
    public DexResponse<Map<String, BigInteger>> getTransactionCount(@RequestParam("address") String address) throws Exception {
	    if (address != null) {
	        return DexResponse.buildResponse(ethereumInfoService.getTransactionCount(address));
        } else {
            return DexResponse.buildResponse(new HashMap<>());
        }
    }

    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_BY_HASH, method = RequestMethod.GET)
    public DexResponse<EthTransactionResultDTO> getTransaction(@RequestParam("txHash") String txHash) throws Exception {
        return DexResponse.buildResponse(ethereumInfoService.getEthTransaction(txHash));
    }

    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_ETHEREUM_GET_TRANSACTION_RECEIPT_BY_HASH, method = RequestMethod.GET)
    public DexResponse<EthTransactionReceiptResultDTO> getTransactionReceipt(@RequestParam("txHash") String txHash) throws Exception {
        if (txHash != null) {
            return DexResponse.buildResponse(ethereumInfoService.getEthTransactionReceipt(txHash));
        } else {
            return DexResponse.buildResponse(null);
        }
    }

    /**
     * get klay account
     *
     * @param postBody
     * @return
     * @throws TalkenException
     */
    //    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GETACCOUNT, method = RequestMethod.POST)
    public DexResponse<Account.AccountData> getKlayAccount(@RequestBody EthBalanceRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(klaytnInfoService.getAccount(postBody.getAddress()));
    }

    /**
     * get klay balance
     *
     * @param postBody
     * @return
     * @throws TalkenException
     */
    //    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GETBALANCE, method = RequestMethod.POST)
    public DexResponse<BigInteger> getKlayBalance(@RequestBody EthBalanceRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(klaytnInfoService.getBalance(postBody.getAddress()));
    }

    /**
     * get klay gasPrice
     *
     * @return
     * @throws TalkenException
     */
    //    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GASPRICE, method = RequestMethod.GET)
    public DexResponse<BigInteger> getKlayGasPrice() throws TalkenException {
        return DexResponse.buildResponse(klaytnInfoService.getGasPrice());
    }

    /**
     * get klay transaction
     *
     * @param txHash
     * @return
     * @throws TalkenException
     */
    //    @AuthRequired
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
     * @param txHash
     * @return
     * @throws TalkenException
     */
    //    @AuthRequired
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
     * @param postBody
     * @return
     * @throws TalkenException
     */
    //    @AuthRequired
    @RequestMapping(value = RequestMappings.BLOCK_CHAIN_KLAYTN_GET_TRANSACTION_LIST, method = RequestMethod.POST)
    public DexResponse<TransferArray> getKlayTransactionReceipt(@RequestBody EthBalanceRequest postBody) throws TalkenException {
        DTOValidator.validate(postBody);
        return DexResponse.buildResponse(klaytnInfoService.getTransactionList(postBody.getAddress()));
    }

	/**
	 * get Bsc balance
	 *
	 * @param postBody
	 * @return
	 * @throws TalkenException
	 */
	//    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GETBALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getBscBalance(@RequestBody EthBalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(bscInfoService.getBscBalance(postBody.getAddress()));
	}

	/**
	 * get bep20 balance
	 *
	 * @param postBody
	 * @return
	 * @throws TalkenException
	 */
//	@AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GETBEP20BALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getBep20Balance(@RequestBody Erc20BalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(bscInfoService.getBep20Balance(postBody.getContract(), postBody.getAddress()));
	}

	/**
	 * get Bsc gasPrice
	 *
	 * @return
	 * @throws TalkenException
	 */
	//    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GASPRICE, method = RequestMethod.GET)
	public DexResponse<BscGasPriceResult> getBscGasPriceAndLimit() throws TalkenException {
		return DexResponse.buildResponse(bscInfoService.getGasPriceAndLimit());
	}

	/**
	 * get bsc transaction by hash
	 * @param txHash
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GET_TRANSACTION_BY_HASH, method = RequestMethod.GET)
	public DexResponse<EthTransactionResultDTO> getBscTransaction(@RequestParam("txHash") String txHash) throws Exception {
		return DexResponse.buildResponse(bscInfoService.getBscTransaction(txHash));
	}

	/**
	 * get bsc transaction receipt by hash
	 * @param txHash
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_BSC_GET_TRANSACTION_RECEIPT_BY_HASH, method = RequestMethod.GET)
	public DexResponse<EthTransactionReceiptResultDTO> getBscTransactionReceipt(@RequestParam("txHash") String txHash) throws Exception {
		if (txHash != null) {
			return DexResponse.buildResponse(bscInfoService.getBscTransactionReceipt(txHash));
		} else {
			return DexResponse.buildResponse(null);
		}
	}

	/**
	 * get Heco balance
	 *
	 * @param postBody
	 * @return
	 * @throws TalkenException
	 */
	//    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_GETBALANCE, method = RequestMethod.POST)
	public DexResponse<BigInteger> getHecoBalance(@RequestBody EthBalanceRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(hecoInfoService.getHecoBalance(postBody.getAddress()));
	}

	/**
	 * get Heco gasPrice
	 *
	 * @return
	 * @throws TalkenException
	 */
	//    @AuthRequired
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_GASPRICE, method = RequestMethod.GET)
	public DexResponse<HecoGasPriceResult> getHecoGasPriceAndLimit() throws TalkenException {
		return DexResponse.buildResponse(hecoInfoService.getGasPriceAndLimit());
	}

	/**
	 * get heco transaction by hash
	 * @param txHash
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_GET_TRANSACTION_BY_HASH, method = RequestMethod.GET)
	public DexResponse<EthTransactionResultDTO> getHecoTransaction(@RequestParam("txHash") String txHash) throws Exception {
		return DexResponse.buildResponse(hecoInfoService.getHecoTransaction(txHash));
	}

	/**
	 * get heco transaction receipt by hash
	 * @param txHash
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = RequestMappings.BLOCK_CHAIN_HECO_GET_TRANSACTION_RECEIPT_BY_HASH, method = RequestMethod.GET)
	public DexResponse<EthTransactionReceiptResultDTO> getHecoTransactionReceipt(@RequestParam("txHash") String txHash) throws Exception {
		if (txHash != null) {
			return DexResponse.buildResponse(hecoInfoService.getHecoTransactionReceipt(txHash));
		} else {
			return DexResponse.buildResponse(null);
		}
	}
}