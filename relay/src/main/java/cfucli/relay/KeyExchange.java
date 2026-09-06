package cfucli.relay;

import module java.base;

/** X25519, so the traffic key is never the password. Public keys travel in X.509 form because
 *  that round-trips through the JDK without hand-rolling the little-endian u-coordinate. */
public final class KeyExchange {

    public static final String ALGORITHM = "X25519", AGREEMENT = "XDH";

    public static KeyPair newKeyPair() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] encode(PublicKey key) {
        return key.getEncoded();
    }

    public static PublicKey decode(byte[] x509) {
        try {
            return KeyFactory.getInstance(AGREEMENT).generatePublic(new X509EncodedKeySpec(x509));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("not an X25519 public key", e);
        }
    }

    public static byte[] agree(PrivateKey own, PublicKey peer) {
        try {
            var ka = KeyAgreement.getInstance(AGREEMENT);
            ka.init(own);
            ka.doPhase(peer, true);
            return ka.generateSecret();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("key agreement failed", e);
        }
    }

    private KeyExchange() {}
}
