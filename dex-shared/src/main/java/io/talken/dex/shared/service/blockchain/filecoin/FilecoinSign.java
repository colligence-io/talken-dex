package io.talken.dex.shared.service.blockchain.filecoin;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import io.talken.common.util.PrefixedLogger;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import ove.crypto.digest.Blake2b;
import shadow.com.google.common.base.Joiner;
import shadow.com.google.common.collect.ImmutableList;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;

import static org.web3j.crypto.Sign.signMessage;

/**
 * The type Filecoin sign.
 */
public class FilecoinSign {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(FilecoinSign.class);

    /**
     * The constant CID_PREFIX.
     */
    public static byte[] CID_PREFIX = new byte[]{0x01, 0x71, (byte) 0xa0, (byte) 0xe4, 0x02, 0x20};

    /**
     * The constant FIL_HARDENED.
     */
    public static final ChildNumber FIL_HARDENED = new ChildNumber(461, true);

    private static SignData transaction(FilecoinTransaction tran) {
        byte[] from = getByte(tran.getFrom());
        byte[] to = getByte(tran.getTo());
        SignData signData = new SignData();
        signData.setVersion(new UnsignedInteger(0));
        signData.setTo(new ByteString(to));
        signData.setFrom(new ByteString(from));
        signData.setNonce(new UnsignedInteger(tran.getNonce()));
        ByteString valueByteString;
        if (new BigInteger(tran.getValue()).toByteArray()[0] != 0) {
            byte[] byte1 = new byte[new BigInteger(tran.getValue()).toByteArray().length + 1];
            byte1[0] = 0;
            System.arraycopy(new BigInteger(tran.getValue()).toByteArray(), 0, byte1, 1, new BigInteger(tran.getValue()).toByteArray().length);
            valueByteString = new ByteString(byte1);
        } else {
            valueByteString = new ByteString(new BigInteger(tran.getValue()).toByteArray());
        }

        signData.setValue(valueByteString);
        signData.setGasLimit(new UnsignedInteger(tran.getGasLimit()));

        ByteString gasFeeCapString;
        if (new BigInteger(tran.getGasFeeCap()).toByteArray()[0] != 0) {
            byte[] byte2 = new byte[new BigInteger(tran.getGasFeeCap()).toByteArray().length + 1];
            byte2[0] = 0;
            System.arraycopy(new BigInteger(tran.getGasFeeCap()).toByteArray(), 0, byte2, 1
                    , new BigInteger(tran.getGasFeeCap()).toByteArray().length);
            gasFeeCapString = new ByteString(byte2);
        } else {
            gasFeeCapString = new ByteString(new BigInteger(tran.getGasFeeCap()).toByteArray());
        }
        signData.setGasFeeCap(gasFeeCapString);

        ByteString gasGasPremium;
        if (new BigInteger(tran.getGasPremium()).toByteArray()[0] != 0) {
            byte[] byte2 = new byte[new BigInteger(tran.getGasPremium()).toByteArray().length + 1];
            byte2[0] = 0;
            System.arraycopy(new BigInteger(tran.getGasPremium()).toByteArray(), 0, byte2, 1
                    ,new BigInteger(tran.getGasPremium()).toByteArray().length);
            gasGasPremium = new ByteString(byte2);
        } else {
            gasGasPremium = new ByteString(new BigInteger(tran.getGasPremium()).toByteArray());
        }

        signData.setGasPremium(gasGasPremium);

        signData.setMethodNum(new UnsignedInteger(0));
        signData.setParams(new ByteString(new byte[0]));
        return signData;
    }

    /**
     * Sign transaction string.
     *
     * @param tran          the tran
     * @param mnemonicWords the mnemonic words
     * @return the string
     */
    public static String signTransaction(FilecoinTransaction tran, List<String> mnemonicWords) {
        logger.debug("start signTransaction with Transaction {}", tran);
        SignData signData = transaction(tran);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).encode(new CborBuilder()
                    .addArray()
                    .add(signData.getVersion())
                    .add(signData.getTo())
                    .add(signData.getFrom())
                    .add(signData.getNonce())
                    .add(signData.getValue())
                    .add(signData.getGasLimit())
                    .add(signData.getGasFeeCap())
                    .add(signData.getGasPremium())
                    .add(signData.getMethodNum())
                    .add(new ByteString(new byte[]{}))
                    .end()
                    .build());
            byte[] encodedBytes = baos.toByteArray();
            byte[] cidHashBytes = getCidHash(encodedBytes);

            return sign(cidHashBytes,mnemonicWords);
        } catch (CborException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] getCidHash(byte[] message) {
        byte[] messageByte = Blake2b.Digest.newInstance(32).digest(message);
        int xlen = CID_PREFIX.length;
        int ylen = messageByte.length;
        byte[] result = new byte[xlen + ylen];

        System.arraycopy(CID_PREFIX, 0, result, 0, xlen);
        System.arraycopy(messageByte, 0, result, xlen, ylen);

        byte[] prefixByte = Blake2b.Digest.newInstance(32).digest(result);
        String prefixByteHex = Numeric.toHexString(prefixByte).substring(2);
        logger.debug("prefixByteHex={}", prefixByteHex);
        return prefixByte;
    }

    private static String sign(byte[] cidHash,List<String> mnemonicWords) {
        String words = Joiner.on(" ").join(mnemonicWords);
        byte[] seed = MnemonicUtils.generateSeed(words, "");
        DeterministicKey rootPrivateKey = HDKeyDerivation.createMasterPrivateKey(seed);
        DeterministicHierarchy deterministicHierarchy = new DeterministicHierarchy(rootPrivateKey);
        ImmutableList<ChildNumber> path = ImmutableList.of(new ChildNumber(44, true), FIL_HARDENED, ChildNumber.ZERO_HARDENED);
        DeterministicKey fourpath = deterministicHierarchy.get(path, true, true);
        DeterministicKey fourpathhd = HDKeyDerivation.deriveChildKey(fourpath, 0);
        DeterministicKey fivepathhd = HDKeyDerivation.deriveChildKey(fourpathhd, 0);
        ECKeyPair ecKeyPair = ECKeyPair.create(fivepathhd.getPrivKeyBytes());
        Sign.SignatureData signatureData = signMessage(cidHash,ecKeyPair, false);
        byte[] sig = getSignature(signatureData);
        String stringHex = Numeric.toHexString(sig).substring(2);
        String base64 = Base64.getEncoder().encodeToString(sig);
        logger.debug("stringHex=={}", stringHex);
        logger.debug("base64=={}", base64);
        return base64;
    }

    private static byte[] getSignature(Sign.SignatureData signatureData) {
        byte[] sig = new byte[65];
        System.arraycopy(signatureData.getR(), 0, sig, 0, 32);
        System.arraycopy(signatureData.getS(), 0, sig, 32, 32);
        sig[64] = (byte) ((signatureData.getV()[0] & 0xFF) - 27);
        return sig;
    }

    private static byte[] getByte(String addressStr) {
        String str = addressStr.substring(2);
        byte[] bytes12 = new byte[21];
        bytes12[0] = 1;
        System.arraycopy(Base32New.decode(str), 0, bytes12, 1, 20);
        return bytes12;
    }
}
