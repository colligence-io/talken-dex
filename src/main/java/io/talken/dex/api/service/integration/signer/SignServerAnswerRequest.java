package io.talken.dex.api.service.integration.signer;

import lombok.Data;

@Data
public class SignServerAnswerRequest {
	String myNameIs;
	String yourQuestionWas;
	String myAnswerIs;
}
