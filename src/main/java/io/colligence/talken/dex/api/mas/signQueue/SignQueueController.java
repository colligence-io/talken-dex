package io.colligence.talken.dex.api.mas.signQueue;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.DexResponse;
import io.colligence.talken.dex.api.RequestMappings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SignQueueController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SignQueueController.class);

	@Autowired
	private SignQueueService signQueueService;

	@RequestMapping(value = RequestMappings.SIGNTASK_LIST, method = RequestMethod.GET)
	public DexResponse<String> taskList() throws DexException {
		return DexResponse.buildResponse("Not Implemented");
	}

	@RequestMapping(value = RequestMappings.UPDATE_SIGNTASK, method = RequestMethod.GET)
	public DexResponse<String> updateTask() throws DexException {
		return DexResponse.buildResponse("Not Implemented");
	}
}
