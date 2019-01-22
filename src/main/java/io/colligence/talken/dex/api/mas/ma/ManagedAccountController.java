package io.colligence.talken.dex.api.mas.ma;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.collection.SingleKeyTable;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.DexResponse;
import io.colligence.talken.dex.api.RequestMappings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManagedAccountController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(ManagedAccountController.class);

	@Autowired
	private ManagedAccountService maService;

	@RequestMapping(value = RequestMappings.MALIST, method = RequestMethod.GET)
	public DexResponse<SingleKeyTable<String, ManagedAccountPack>> maList() throws DexException {
		return DexResponse.buildResponse(maService.getPackList());
	}

	@RequestMapping(value = RequestMappings.SWAPHOLDER, method = RequestMethod.GET)
	public DexResponse<String> swapHolder() throws DexException {
		return DexResponse.buildResponse("Not Implemented");
	}
}
