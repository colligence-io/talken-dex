package io.talken.dex.shared.service.blockchain.ethereum;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

/**
 * Standard ERC20 ABI Functions
 */
public abstract class StandardERC20ContractFunctions {
    /**
     * Name function.
     *
     * @return the function
     */
    public static Function name() {
		return new Function(
				"name",
				Collections.emptyList(),
				Collections.singletonList(new TypeReference<Utf8String>() {}));
	}

    /**
     * Symbol function.
     *
     * @return the function
     */
    public static Function symbol() {
		return new Function(
				"symbol",
				Collections.emptyList(),
				Collections.singletonList(new TypeReference<Utf8String>() {}));
	}

    /**
     * Decimals function.
     *
     * @return the function
     */
    public static Function decimals() {
		return new Function(
				"decimals",
				Collections.emptyList(),
				Collections.singletonList(new TypeReference<Uint8>() {}));
	}

    /**
     * Total supply function.
     *
     * @return the function
     */
    public static Function totalSupply() {
		return new Function(
				"totalSupply",
				Collections.emptyList(),
				Collections.singletonList(new TypeReference<Uint256>() {}));
	}

    /**
     * Balance of function.
     *
     * @param owner the owner
     * @return the function
     */
    public static Function balanceOf(String owner) {
		return new Function(
				"balanceOf",
				Collections.singletonList(new Address(owner)),
				Collections.singletonList(new TypeReference<Uint256>() {}));
	}

    /**
     * Transfer function.
     *
     * @param to    the to
     * @param value the value
     * @return the function
     */
    public static Function transfer(String to, BigInteger value) {
		return new Function(
				"transfer",
				Arrays.asList(new Address(to), new Uint256(value)),
				Collections.singletonList(new TypeReference<Bool>() {}));
	}

    /**
     * Allowance function.
     *
     * @param owner   the owner
     * @param spender the spender
     * @return the function
     */
    public static Function allowance(String owner, String spender) {
		return new Function(
				"allowance",
				Arrays.asList(new Address(owner), new Address(spender)),
				Collections.singletonList(new TypeReference<Uint256>() {}));
	}

    /**
     * Approve function.
     *
     * @param spender the spender
     * @param value   the value
     * @return the function
     */
    public static Function approve(String spender, BigInteger value) {
		return new Function(
				"approve",
				Arrays.asList(new Address(spender), new Uint256(value)),
				Collections.singletonList(new TypeReference<Bool>() {}));
	}

    /**
     * Transfer from function.
     *
     * @param from  the from
     * @param to    the to
     * @param value the value
     * @return the function
     */
    public static Function transferFrom(String from, String to, BigInteger value) {
		return new Function(
				"transferFrom",
				Arrays.asList(new Address(from), new Address(to), new Uint256(value)),
				Collections.singletonList(new TypeReference<Bool>() {}));
	}

    /**
     * Transfer event event.
     *
     * @return the event
     */
    public static Event transferEvent() {
		return new Event(
				"Transfer",
				Arrays.asList(
						new TypeReference<Address>(true) {},
						new TypeReference<Address>(true) {},
						new TypeReference<Uint256>() {}));
	}

    /**
     * Approval event event.
     *
     * @return the event
     */
    public static Event approvalEvent() {
		return new Event(
				"Approval",
				Arrays.asList(
						new TypeReference<Address>(true) {},
						new TypeReference<Address>(true) {},
						new TypeReference<Uint256>() {}));
	}
}
