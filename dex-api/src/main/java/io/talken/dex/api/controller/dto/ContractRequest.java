package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.ContractTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.UserContract;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ContractRequest {
    @NotNull
    long userId;
    @NotNull
    BlockChainPlatformEnum bctxType;
    @NotNull
    ContractTypeEnum contractType;
    @NotNull
    String contractAddress;
    @NotNull
    String symbol;
    @NotNull
    String name;
    @NotNull
    int decimals;

    public ContractRequest(UserContract userContract) {
        this.userId = userContract.getUserId();
        this.bctxType = userContract.getBctxType();
        this.contractType = userContract.getContractType();
        this.contractAddress = userContract.getContractAddress();
        this.symbol = userContract.getSymbol();
        this.name = userContract.getName();
        this.decimals = userContract.getDecimals();
    }
}
