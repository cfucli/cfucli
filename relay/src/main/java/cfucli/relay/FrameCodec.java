package cfucli.relay;

import module java.base;

/** magic(2) version(1) type(1) seq(8) || AES-256-GCM ciphertext+tag.
 *  The header is the AAD, so a tampered type or sequence number fails the tag rather than being
 *  quietly accepted. The nonce is never transmitted - it is the direction's four-byte prefix
 *  followed by the sequence number, which is unique per key by construction. */
public final class FrameCodec {

    public static final int HEADER_BYTES = 12, NONCE_BYTES = 12, TAG_BITS = 128;
    public static final byte VERSION = 1;

    static final byte MAGIC_0 = 'U', MAGIC_1 = 'C';
    static final String TRANSFORM = "AES/GCM/NoPadding";

    public static byte[] encode(Frame f, Direction d, SessionKeys keys) {
        var payload = f.payload();
        var header = header(f.type(), f.seq());
        var ct = crypt(Cipher.ENCRYPT_MODE, keys, d, f.seq(), header, payload, 0, payload.length);
        var out = new byte[HEADER_BYTES + ct.length];
        System.arraycopy(header, 0, out, 0, HEADER_BYTES);
        System.arraycopy(ct, 0, out, HEADER_BYTES, ct.length);
        return out;
    }

    public static Frame decode(byte[] wire, Direction d, SessionKeys keys) {
        if (wire.length < HEADER_BYTES) throw new IllegalArgumentException("frame shorter than its header");
        if (wire[0] != MAGIC_0 || wire[1] != MAGIC_1) throw new IllegalArgumentException("not an cfucli frame");
        if (wire[2] != VERSION) throw new IllegalArgumentException("frame version " + wire[2] + ", expected " + VERSION);
        var type = FrameType.of(wire[3]);
        var seq = ByteBuffer.wrap(wire, 4, 8).getLong();
        var header = Arrays.copyOf(wire, HEADER_BYTES);
        var plain = crypt(Cipher.DECRYPT_MODE, keys, d, seq, header, wire, HEADER_BYTES, wire.length - HEADER_BYTES);
        return Frame.of(type, seq, plain, d);
    }

    static byte[] header(FrameType type, long seq) {
        return ByteBuffer.allocate(HEADER_BYTES).put(MAGIC_0).put(MAGIC_1).put(VERSION).put(type.code).putLong(seq).array();
    }

    static byte[] nonce(byte[] prefix, long seq) {
        return ByteBuffer.allocate(NONCE_BYTES).put(prefix).putLong(seq).array();
    }

    static byte[] crypt(int mode, SessionKeys keys, Direction d, long seq, byte[] aad, byte[] in, int off, int len) {
        try {
            var c = Cipher.getInstance(TRANSFORM);
            c.init(mode, keys.key(d), new GCMParameterSpec(TAG_BITS, nonce(keys.ivPrefix(d), seq)));
            c.updateAAD(aad);
            return c.doFinal(in, off, len);
        } catch (AEADBadTagException e) {
            throw new SecurityException("frame failed authentication - wrong key, or tampered with", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private FrameCodec() {}
}
