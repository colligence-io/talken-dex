package io.talken.dex.shared.service.blockchain.ethereum;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;


public abstract class StandardERC20ContractFunctions {
	public static Function name() {
		return new Function(
				"name",
				Collections.emptyList(),
				Collections.singletonList(new TypeReference<Utf8String>() {}));
	}

	public static Function symbol() {
		return new Function(
				"symbol",
				Collections.emptyList(),
				Collections.singletonList(new TypeReference<Utf8String>() {}));
	}

	public static Function decimals() {
		return new Function(
				"decimals",
				Collections.emptyList(),
				Collections.singletonList(new TypeReference<Uint8>() {}));
	}

	public static Function totalSupply() {
		return new Function(
				"totalSupply",
				Collections.emptyList(),
				Collections.singletonList(new TypeReference<Uint256>() {}));
	}

	public static Function balanceOf(String owner) {
		return new Function(
				"balanceOf",
				Collections.singletonList(new Address(owner)),
				Collections.singletonList(new TypeReference<Uint256>() {}));
	}

	public static Function transfer(String to, BigInteger value) {
		return new Function(
				"transfer",
				Arrays.asList(new Address(to), new Uint256(value)),
				Collections.singletonList(new TypeReference<Bool>() {}));
	}

	public static Function allowance(String owner, String spender) {
		return new Function(
				"allowance",
				Arrays.asList(new Address(owner), new Address(spender)),
				Collections.singletonList(new TypeReference<Uint256>() {}));
	}

	public static Function approve(String spender, BigInteger value) {
		return new Function(
				"approve",
				Arrays.asList(new Address(spender), new Uint256(value)),
				Collections.singletonList(new TypeReference<Bool>() {}));
	}

	public static Function transferFrom(String from, String to, BigInteger value) {
		return new Function(
				"transferFrom",
				Arrays.asList(new Address(from), new Address(to), new Uint256(value)),
				Collections.singletonList(new TypeReference<Bool>() {}));
	}

	public static Event transferEvent() {
		return new Event(
				"Transfer",
				Arrays.asList(
						new TypeReference<Address>(true) {},
						new TypeReference<Address>(true) {},
						new TypeReference<Uint256>() {}));
	}

	public static Event approvalEvent() {
		return new Event(
				"Approval",
				Arrays.asList(
						new TypeReference<Address>(true) {},
						new TypeReference<Address>(true) {},
						new TypeReference<Uint256>() {}));
	}
}
