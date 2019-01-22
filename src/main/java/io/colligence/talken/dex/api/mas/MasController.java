package io.colligence.talken.dex.api.mas;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.collection.SingleKeyTable;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.DexResponse;
import io.colligence.talken.dex.api.RequestMappings;
import io.colligence.talken.dex.service.mas.ManagedAccountPack;
import io.colligence.talken.dex.service.mas.ManagedAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MasController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(MasController.class);

	@Autowired
	private ManagedAccountService maService;

	@RequestMapping(value = RequestMappings.MALIST, method = RequestMethod.GET)
	public DexResponse<SingleKeyTable<String, ManagedAccountPack>> anchor() throws DexException {
		return DexResponse.buildResponse(maService.getPackList());
	}
}
