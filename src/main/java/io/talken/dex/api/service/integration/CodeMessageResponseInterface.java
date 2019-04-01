package io.talken.dex.api.service.integration;

public interface CodeMessageResponseInterface {
	boolean isSuccess();

	int getCode();

	String getMessage();
}
