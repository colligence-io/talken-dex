package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigInteger;

@Data
public class HecoGasPriceResult {
    private BigInteger gasPrice;
    private BigInteger gasLimit;
}
