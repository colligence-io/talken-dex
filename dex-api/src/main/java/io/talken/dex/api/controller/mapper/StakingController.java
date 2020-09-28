package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DTOValidator;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.CreateStakingRequest;
import io.talken.dex.api.controller.dto.CreateStakingResult;
import io.talken.dex.api.controller.dto.StakingEventDTO;
import io.talken.dex.api.service.StakingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotEmpty;

@RestController
public class StakingController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StakingController.class);

	@Autowired
	private StakingService stakingService;

	@Autowired
	private AuthInfo authInfo;

    @AuthRequired
	@RequestMapping(value = RequestMappings.STAKING, method = RequestMethod.POST)
	public DexResponse<CreateStakingResult> createStaking(@RequestBody CreateStakingRequest postBody) throws TalkenException {
		return DexResponse.buildResponse(stakingService.createStaking(authInfo.getUser(), postBody));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.UNSTAKING, method = RequestMethod.POST)
	public DexResponse<CreateStakingResult> createUnStaking(@RequestBody CreateStakingRequest postBody) throws TalkenException {
		DTOValidator.validate(postBody);
		return DexResponse.buildResponse(stakingService.createUnStaking(authInfo.getUser(), postBody));
	}

	@AuthRequired
	@RequestMapping(value = RequestMappings.STAKING_AVAILABLE, method = RequestMethod.POST)
	public DexResponse<Boolean> checkAvailable(@RequestBody CreateStakingRequest postBody) {
		return DexResponse.buildResponse(stakingService.checkStakingAvailable(authInfo.getUser(), postBody));
	}

//    @RequestMapping(value = RequestMappings.STAKING_LIST, method = RequestMethod.GET)
//    public DexResponse<Boolean> getStakingList() {
//        return DexResponse.buildResponse(stakingService.checkAvailable(authInfo.getUser(), postBody));
//    }
//
//    @RequestMapping(value = RequestMappings.STAKING_CODE, method = RequestMethod.GET)
//    public DexResponse<Boolean> getStakingListByCode(
//            @PathVariable @NotEmpty String stakingCode,
//            @RequestParam("assetCode") String assetCode
//    ) {
//        return DexResponse.buildResponse(stakingService.checkAvailable(authInfo.getUser(), postBody));
//    }

    @RequestMapping(value = RequestMappings.STAKING_DETAIL, method = RequestMethod.GET)
    public DexResponse<StakingEventDTO> getStakingDetail(
            @PathVariable @NotEmpty long stakingId
    ) throws TalkenException {
        return DexResponse.buildResponse(stakingService.getStakingEvent(stakingId));
    }
}
