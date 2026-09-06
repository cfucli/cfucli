package cfucli.relay;

import module java.base;

/** The one-time password, stretched. This never encrypts anything - it only authenticates the two
 *  public keys during the handshake, so a password recovered long after the fact buys nothing. */
public final class PairingSecret {

    public static final int ITERATIONS = 210_000, BITS = 256;

    public static byte[] derive(String password, String sessionId) {
        try {
            var salt = ("cfucli/v1/pairing/" + sessionId).getBytes(StandardCharsets.UTF_8);
            var spec = new PBEKeySpec(Ids.normalise(password).toCharArray(), salt, ITERATIONS, BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot derive pairing secret", e);
        }
    }

    public static byte[] tag(byte[] pairing, String role, byte[] publicKey) {
        var role8 = role.getBytes(StandardCharsets.UTF_8);
        var buf = new byte[role8.length + 1 + publicKey.length];
        System.arraycopy(role8, 0, buf, 0, role8.length);
        buf[role8.length] = ':';
        System.arraycopy(publicKey, 0, buf, role8.length + 1, publicKey.length);
        return Hkdf.mac(pairing, buf);
    }

    public static boolean verify(byte[] pairing, String role, byte[] publicKey, byte[] claimed) {
        return Hkdf.macEquals(tag(pairing, role, publicKey), claimed);
    }

    private PairingSecret() {}
}
