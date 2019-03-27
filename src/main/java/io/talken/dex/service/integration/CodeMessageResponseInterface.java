package io.talken.dex.service.integration;

public interface CodeMessageResponseInterface {
	boolean isSuccess();

	int getCode();

	String getMessage();
}
