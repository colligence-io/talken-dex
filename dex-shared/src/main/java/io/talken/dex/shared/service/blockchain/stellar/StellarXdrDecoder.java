package io.talken.dex.shared.service.blockchain.stellar;

import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.TransactionResult;
import org.stellar.sdk.xdr.XdrDataInputStream;
import shadow.com.google.common.io.BaseEncoding;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Deprecated
public class StellarXdrDecoder {

	public static TransactionResult decodeResultXdr(TransactionResponse response) throws IOException {
		// decode result
		byte[] bytes = BaseEncoding.base64().decode(response.getResultXdr());
		ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
		XdrDataInputStream xdrInputStream = new XdrDataInputStream(inputStream);
		return TransactionResult.decode(xdrInputStream);
	}
}
