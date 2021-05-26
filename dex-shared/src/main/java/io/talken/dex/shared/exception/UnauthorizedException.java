package io.talken.dex.shared.exception;


import io.talken.dex.shared.exception.auth.AuthenticationException;

/**
 * The type Unauthorized exception.
 */
public class UnauthorizedException extends DexException {
	private static final long serialVersionUID = -7795823407692828677L;

    /**
     * Instantiates a new Unauthorized exception.
     *
     * @param cause the cause
     */
    public UnauthorizedException(AuthenticationException cause) {
		super(cause, DexExceptionTypeEnum.UNAUTHORIZED, cause.getMessage());
	}
}
