package io.talken.dex.shared.service.blockchain.filecoin;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import lombok.Data;

@Data
public class SignData {
    private UnsignedInteger version;
    private ByteString from;
    private ByteString to;
    private UnsignedInteger nonce;
    private ByteString value;
    private ByteString gasFeeCap;
    private ByteString gasPremium;
    private UnsignedInteger gasLimit;
    private UnsignedInteger methodNum;
    private ByteString params;
}
