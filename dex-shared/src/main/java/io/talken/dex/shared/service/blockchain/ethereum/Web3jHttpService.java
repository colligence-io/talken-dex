package io.talken.dex.shared.service.blockchain.ethereum;

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import org.web3j.protocol.http.HttpService;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Web3jHttpService extends HttpService {
	// static block adopted from web3j HttpService
	private static final CipherSuite[] INFURA_CIPHER_SUITES;
	private static final ConnectionSpec INFURA_CIPHER_SUITE_SPEC;
	private static final List<ConnectionSpec> CONNECTION_SPEC_LIST;

	static {
		INFURA_CIPHER_SUITES = new CipherSuite[]{CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256, CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256, CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384, CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384, CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256, CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256, CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA, CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA, CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA, CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA, CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256, CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384, CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA, CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA, CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA, CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256, CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384, CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256, CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256};
		INFURA_CIPHER_SUITE_SPEC = (new okhttp3.ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)).cipherSuites(INFURA_CIPHER_SUITES).build();
		CONNECTION_SPEC_LIST = Arrays.asList(INFURA_CIPHER_SUITE_SPEC, ConnectionSpec.CLEARTEXT);
	}

	public Web3jHttpService(String url) {
		super(url, createOkHttpClient());
	}

	public Web3jHttpService(String url, boolean includeRawResponse) {
		super(url, createOkHttpClient(), includeRawResponse);
	}

	// static block adopted from web3j HttpService
	// timeout changed from 10 sec -> 30 sec
	private static OkHttpClient createOkHttpClient() {
		OkHttpClient.Builder builder = (new OkHttpClient.Builder()).connectionSpecs(CONNECTION_SPEC_LIST)
				.connectTimeout(30, TimeUnit.SECONDS)
				.readTimeout(30, TimeUnit.SECONDS)
				.writeTimeout(30, TimeUnit.SECONDS);
		return builder.build();
	}
}
