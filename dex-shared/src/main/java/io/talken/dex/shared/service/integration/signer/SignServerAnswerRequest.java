package io.talken.dex.shared.service.integration.signer;

import lombok.Data;

/**
 * The type Sign server answer request.
 */
@Data
public class SignServerAnswerRequest {
    /**
     * The My name is.
     */
    String myNameIs;
    /**
     * The Your question was.
     */
    String yourQuestionWas;
    /**
     * The My answer is.
     */
    String myAnswerIs;
}
