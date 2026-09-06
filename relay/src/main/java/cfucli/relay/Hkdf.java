package cfucli.relay;

import module java.base;

public final class Hkdf {

    static final String ALG = "HmacSHA256";
    static final int HASH_LEN = 32;

    public static byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length) {
        return expand(extract(salt, ikm), info, length);
    }

    public static byte[] extract(byte[] salt, byte[] ikm) {
        return mac(salt == null || salt.length == 0 ? new byte[HASH_LEN] : salt, ikm);
    }

    public static byte[] expand(byte[] prk, byte[] info, int length) {
        if (length > 255 * HASH_LEN) throw new IllegalArgumentException("hkdf length " + length);
        var out = new byte[length];
        var t = new byte[0];
        var pos = 0;
        for (var counter = 1; pos < length; counter++) {
            var in = new byte[t.length + info.length + 1];
            System.arraycopy(t, 0, in, 0, t.length);
            System.arraycopy(info, 0, in, t.length, info.length);
            in[in.length - 1] = (byte) counter;
            t = mac(prk, in);
            var n = Math.min(t.length, length - pos);
            System.arraycopy(t, 0, out, pos, n);
            pos += n;
        }
        return out;
    }

    public static byte[] mac(byte[] key, byte[] data) {
        try {
            var m = Mac.getInstance(ALG);
            m.init(new SecretKeySpec(key, ALG));
            return m.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean macEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    private Hkdf() {}
}
