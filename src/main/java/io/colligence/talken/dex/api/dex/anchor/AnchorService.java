package io.colligence.talken.dex.api.dex.anchor;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.DexTaskId;
import io.colligence.talken.dex.api.dex.anchor.dto.AnchorResult;
import io.colligence.talken.dex.api.dex.anchor.dto.AnchorSubmitResult;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorResult;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorSubmitResult;
import io.colligence.talken.dex.exception.APICallException;
import io.colligence.talken.dex.exception.APIErrorException;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import io.colligence.talken.dex.service.StellarNetworkService;
import io.colligence.talken.dex.service.integration.AncServerAnchorRequest;
import io.colligence.talken.dex.service.integration.AncServerAnchorResponse;
import io.colligence.talken.dex.service.integration.AnchorServerService;
import io.colligence.talken.dex.service.mas.ManagedAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("singleton")
public class AnchorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorService.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private AnchorServerService anchorServerService;

	@Autowired
	private ManagedAccountService maService;


	public AnchorResult buildAnchorRequestInformation(String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount) throws AssetTypeNotFoundException, APIErrorException, APICallException {
		String assetHolderAddress = maService.getHolderAccountAddress(assetCode);

		String taskID = DexTaskId.generate(DexTaskId.Type.ANCHOR).toString();
		// TODO : unique check, insert into db

		AncServerAnchorRequest req = new AncServerAnchorRequest();
		req.setTaskID(taskID);
		req.setUid("0"); // TODO : use actual uid
		req.setFrom(privateWalletAddress);
		req.setTo(assetHolderAddress);
		req.setStellar(tradeWalletAddress);
		req.setSymbol(assetCode);
		req.setValue(amount.floatValue());
		req.setMemo("");

		AncServerAnchorResponse asar = anchorServerService.requestAnchor(req);

		AnchorResult result = new AnchorResult();
		result.setTaskID(taskID);
		result.setHolderAccountAddress(asar.getData().getAddress());
		return result;
	}

	public AnchorSubmitResult submitAnchorTransaction(String taskID, String txHash, String txXdr) throws DexException {
		return new AnchorSubmitResult(stellarNetworkService.submitTx(taskID, txHash, txXdr));
	}

	public DeanchorResult buildDeanchorRequestInformation() {
		return new DeanchorResult();
	}

	public DeanchorSubmitResult submitDeanchorTransaction(String taskID, String txHash, String txXdr) throws DexException {
		return new DeanchorSubmitResult(stellarNetworkService.submitTx(taskID, txHash, txXdr));
	}
}
