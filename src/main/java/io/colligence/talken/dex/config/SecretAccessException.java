package io.colligence.talken.dex.config;

public class SecretAccessException extends RuntimeException {
	private static final long serialVersionUID = -5324394693214416429L;

	public SecretAccessException(String s) {
		super(s);
	}

	public SecretAccessException(String s, Throwable throwable) {
		super(s, throwable);
	}
}
