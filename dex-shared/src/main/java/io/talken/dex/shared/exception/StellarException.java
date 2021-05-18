package io.talken.dex.shared.exception;

import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import org.stellar.sdk.requests.ErrorResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

/**
 * The type Stellar exception.
 */
public class StellarException extends DexException {
	private static final long serialVersionUID = 7617859538339055069L;

    /**
     * From stellar exception.
     *
     * @param error the error
     * @return the stellar exception
     */
    public static StellarException from(ErrorResponse error) {
		if(error.getCause() != null)
			return new StellarException(error.getCause(), String.valueOf(error.getCode()), error.getBody());
		else return new StellarException(String.valueOf(error.getCode()), error.getBody());
	}

    /**
     * From stellar exception.
     *
     * @param cause the cause
     * @return the stellar exception
     */
    public static StellarException from(SubmitTransactionResponse cause) {
		ObjectPair<String, String> resultCodesFromExtra = StellarConverter.getResultCodesFromExtra(cause);
		return new StellarException(resultCodesFromExtra.first(), resultCodesFromExtra.second());
	}

    /**
     * Instantiates a new Stellar exception.
     *
     * @param cause the cause
     */
    public StellarException(Throwable cause) {
		this(cause, cause.getClass().getSimpleName(), cause.getMessage());
	}

	private StellarException(String code, String message) {
		super(DexExceptionTypeEnum.STELLAR_EXCEPTION, code, message);
	}

	private StellarException(Throwable cause, String code, String message) {
		super(cause, DexExceptionTypeEnum.STELLAR_EXCEPTION, code, message);
	}
}
