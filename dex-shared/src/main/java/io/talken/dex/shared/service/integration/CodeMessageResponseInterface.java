package io.talken.dex.shared.service.integration;

public interface CodeMessageResponseInterface {
	boolean isSuccess();

	String getCode();

	String getMessage();
}
