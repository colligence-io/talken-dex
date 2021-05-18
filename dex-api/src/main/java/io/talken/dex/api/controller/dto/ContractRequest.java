package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.ContractTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.UserContract;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * The type Contract request.
 */
@Data
public class ContractRequest {
    /**
     * The User id.
     */
    @NotNull
    long userId;
    /**
     * The Bctx type.
     */
    @NotNull
    BlockChainPlatformEnum bctxType;
    /**
     * The Contract type.
     */
    @NotNull
    ContractTypeEnum contractType;
    /**
     * The Contract address.
     */
    @NotNull
    String contractAddress;
    /**
     * The Symbol.
     */
    @NotNull
    String symbol;
    /**
     * The Name.
     */
    @NotNull
    String name;
    /**
     * The Decimals.
     */
    @NotNull
    int decimals;

    /**
     * Instantiates a new Contract request.
     *
     * @param userContract the user contract
     */
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
