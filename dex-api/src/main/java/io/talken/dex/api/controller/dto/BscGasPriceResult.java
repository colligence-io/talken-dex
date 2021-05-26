package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigInteger;

/**
 * The type Bsc gas price result.
 */
@Data
public class BscGasPriceResult {
    private BigInteger gasPrice;
    private BigInteger gasLimit;
}
