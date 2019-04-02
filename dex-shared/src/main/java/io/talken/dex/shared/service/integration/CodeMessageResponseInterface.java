package io.talken.dex.shared.service.integration;

public interface CodeMessageResponseInterface {
	boolean isSuccess();

	int getCode();

	String getMessage();
}
