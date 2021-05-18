package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * The type Transaction hash request.
 */
@Data
public class TransactionHashRequest {
    @NotEmpty
    private String txHash;
}
