package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigInteger;

/**
 * The type Luniverse gas price result.
 */
@Data
public class LuniverseGasPriceResult {
	private BigInteger gasPrice;
	private BigInteger gasLimit;
}
