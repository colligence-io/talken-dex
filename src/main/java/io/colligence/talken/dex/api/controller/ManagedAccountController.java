package io.colligence.talken.dex.api.controller;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.api.service.ManagedAccountPack;
import io.colligence.talken.dex.api.dto.UpdateHolderRequest;
import io.colligence.talken.dex.api.dto.UpdateHolderResult;
import io.colligence.talken.dex.api.service.ManagedAccountService;
import io.colligence.talken.dex.exception.ParameterViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Hashtable;

@RestController
public class ManagedAccountController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(ManagedAccountController.class);

	@Autowired
	private ManagedAccountService maService;

	@RequestMapping(value = RequestMappings.MA_LIST, method = RequestMethod.GET)
	public DexResponse<Hashtable<String, ManagedAccountPack>> maList() throws DexException {
		return DexResponse.buildResponse(maService.getPackList());
	}

	@RequestMapping(value = RequestMappings.MA_RELOAD, method = RequestMethod.GET)
	public DexResponse<Hashtable<String, ManagedAccountPack>> reload() throws DexException {
		return DexResponse.buildResponse(maService.reload());
	}

	@RequestMapping(value = RequestMappings.MA_UPDATEHOLDER, method = RequestMethod.POST)
	public DexResponse<UpdateHolderResult> updateHolder(@RequestBody UpdateHolderRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		if(postBody.getIsActive() == null && postBody.getIsHot() == null)
			throw new ParameterViolationException("At lease one of isActive and isHot must be set.");
		if(postBody.getIsActive() != null && postBody.getIsHot() != null && !postBody.getIsHot() && postBody.getIsActive())
			throw new ParameterViolationException("isHot cannot be false when isActive is true.");
		return DexResponse.buildResponse(maService.updateHolder(postBody.getAssetCode(), postBody.getAddress(), postBody.getIsHot(), postBody.getIsActive()));
	}
}
