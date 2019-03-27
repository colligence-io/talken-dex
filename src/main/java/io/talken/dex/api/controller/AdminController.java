package io.talken.dex.api.controller;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.exception.DexException;
import io.talken.dex.api.dto.UpdateHolderRequest;
import io.talken.dex.api.dto.UpdateHolderResult;
import io.talken.dex.api.service.TokenMetaData;
import io.talken.dex.api.service.TokenMetaService;
import io.talken.dex.exception.ParameterViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotEmpty;
import java.util.Map;

@RestController
public class AdminController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AdminController.class);

	@Autowired
	private TokenMetaService tmService;

	// TODO : Admin Access control


	@RequestMapping(value = RequestMappings.ADM_RELOAD_TM, method = RequestMethod.GET)
	public DexResponse<Map<String, TokenMetaData>> reloadTokenMetaList() throws DexException {
		return DexResponse.buildResponse(tmService.forceReload());
	}

	@RequestMapping(value = RequestMappings.ADM_MI_UPDATEHOLDER, method = RequestMethod.POST)
	public DexResponse<UpdateHolderResult> updateHolder(@PathVariable @NotEmpty String nameKey, @RequestBody UpdateHolderRequest postBody) throws DexException {
		DTOValidator.validate(postBody);
		if(postBody.getIsActive() == null && postBody.getIsHot() == null)
			throw new ParameterViolationException("At lease one of isActive and isHot must be set.");
		if(postBody.getIsActive() != null && postBody.getIsHot() != null && !postBody.getIsHot() && postBody.getIsActive())
			throw new ParameterViolationException("isHot cannot be false when isActive is true.");
		return DexResponse.buildResponse(tmService.updateHolder(nameKey, postBody.getAddress(), postBody.getIsHot(), postBody.getIsActive()));
	}
}
