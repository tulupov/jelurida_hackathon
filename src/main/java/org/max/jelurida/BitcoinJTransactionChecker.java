package org.max.jelurida;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.bouncycastle.util.encoders.Hex;
import org.max.jelurida.Bitcoin2IgnisCurrencyExchange.Params;


/**
 * @author maxtulupov@gmail.com
 */
public class BitcoinJTransactionChecker {

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters CURVE;

    static {
        CURVE = new ECDomainParameters(CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(),
                CURVE_PARAMS.getH());
    }

    private JO message;
    private Bitcoin2IgnisCurrencyExchange.Params params;

    private String txHash;
    private BigInteger r;
    private BigInteger s;
    private Double rate;
    private byte[] pubKey;
    private JO tx;

    public BitcoinJTransactionChecker(JO message, Params params) {
        this.message = message;
        this.params = params;
    }

    public Double validateTxAndConvertAmount() {
            readMessage();
            extractSignature();
            verifySignature();
            findTx();
            return checkTx();
    }

    private void readMessage() {
        txHash = message.getString("txHash");
        r = new BigInteger(message.getString("signatureR"));
        s = new BigInteger(message.getString("signatureS"));
        rate = Double.valueOf(message.getString("exchangeRate"));
    }

    private void extractSignature() {
        try {
            pubKey = null;
            for (int i = 0; i < 4; i++) {
                pubKey = recoverFromSignature(i, r, s, Hex.decode(txHash));
                if (pubKey != null) {
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Can't extract signature", e);
        }
        if (pubKey == null) {
            throw new RuntimeException("Can't recover ECKey from signature");
        }
    }

    private void verifySignature() {
        boolean verificationResult = verify(Hex.decode(txHash), r, s, pubKey);
        if (!verificationResult) {
            throw new RuntimeException("Signature invalid");
        }
    }

    private void findTx() {
        InputStreamReader reader = null;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(params.bitcoinApiUrl() + "rawtx/" + txHash).openConnection();
            connection.connect();
            if (connection.getResponseCode() == 200) {
                reader = new InputStreamReader(connection.getInputStream());
                tx = JO.parse(reader);
            } else {
                throw new RuntimeException("Can't find transaction");
            }
        } catch (IOException e) {
            throw new RuntimeException("Can't find transaction", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {}
            }
        }
    }


    private Double checkTx() {
        String receiverAddress = params.bitcoinReceiverAddress();
        List<JO> addresses = (List<JO>) tx.getArray("out").stream().filter( it ->
            receiverAddress.equals(((JO) it).getString("addr"))
                    || receiverAddress.equals(((JO) it).getString("addr"))
        ).collect(Collectors.toList());
        if (addresses.isEmpty()) {
            throw new RuntimeException("Recepient address doesn't match");
        }

        InputStreamReader reader = null;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(params.bitcoinApiUrl() + "latestblock").openConnection();
            connection.connect();
            if (connection.getResponseCode() == 200) {
                reader = new InputStreamReader(connection.getInputStream());
                JO jo = JO.parse(reader);
                long confirmationsCount = tx.getLong("block_height", 0) - jo.getLong("height", 0) + 1;
                if (confirmationsCount < 0) {
                    confirmationsCount = 0;
                }
                if (confirmationsCount < params.minimumBitcoinTxConfirmationsCount()) {
                    throw new RuntimeException("Not enough confirmation for tx " + txHash);
                }
            } else {
                throw new RuntimeException("Can't get latest block");
            }
        } catch (IOException e) {
            throw new RuntimeException("Can't get latest block", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {}
            }
        }
        return addresses.get(0).getLong("value", 0) * ChildChain.IGNIS.ONE_COIN * rate / 100000000;
    }

    private static byte[] recoverFromSignature(int recId, BigInteger r, BigInteger s, byte[] hash) {
        BigInteger n = CURVE.getN();  // Curve order.
        BigInteger i = BigInteger.valueOf((long) recId / 2);
        BigInteger x = r.add(i.multiply(n));

        BigInteger prime = SecP256K1Curve.q;
        if (x.compareTo(prime) >= 0) {
            // Cannot have point co-ordinates larger than this as everything takes place modulo Q.
            return null;
        }
        ECPoint R = decompressKey(x, (recId & 1) == 1);
        if (!R.multiply(n).isInfinity()) {
            return null;
        }
        BigInteger e = new BigInteger(1, hash);

        BigInteger eInv = BigInteger.ZERO.subtract(e).mod(n);
        BigInteger rInv = r.modInverse(n);
        BigInteger srInv = rInv.multiply(s).mod(n);
        BigInteger eInvrInv = rInv.multiply(eInv).mod(n);
        ECPoint q = ECAlgorithms.sumOfTwoMultiplies(CURVE.getG(), eInvrInv, R, srInv);
        return q.getEncoded(false);
    }

    private static boolean verify(byte[] data, BigInteger r, BigInteger s, byte[] pub) {
        ECDSASigner signer = new ECDSASigner();
        ECPublicKeyParameters params = new ECPublicKeyParameters(CURVE.getCurve().decodePoint(pub), CURVE);
        signer.init(false, params);
        try {
            return signer.verifySignature(data, r, s);
        } catch (NullPointerException e) {
            return false;
        }
    }

    private static ECPoint decompressKey(BigInteger xBN, boolean yBit) {
        X9IntegerConverter x9 = new X9IntegerConverter();
        byte[] compEnc = x9.integerToBytes(xBN, 1 + x9.getByteLength(CURVE.getCurve()));
        compEnc[0] = (byte)(yBit ? 0x03 : 0x02);
        return CURVE.getCurve().decodePoint(compEnc);
    }
}