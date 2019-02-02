package io.colligence.talken.dex.util;

import org.stellar.sdk.xdr.Int64;

import java.time.LocalDateTime;

public class StellarConverter {

	private static final double multiplier = 10000000;

	public static Double toDouble(Int64 value) {
		if(value == null || value.getInt64() == null) return null;
		return value.getInt64().doubleValue() / multiplier;
	}
}
