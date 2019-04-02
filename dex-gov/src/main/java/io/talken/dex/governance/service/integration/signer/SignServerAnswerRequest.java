package io.talken.dex.governance.service.integration.signer;

import lombok.Data;

@Data
public class SignServerAnswerRequest {
	String myNameIs;
	String yourQuestionWas;
	String myAnswerIs;
}
