package cfucli.relay;

import module java.base;

/** One AES-256-GCM key per direction, plus the four-byte nonce prefix that turns a frame's
 *  sequence number into a nonce. Separate keys per direction mean the two ends can both start
 *  their sequence at zero without ever colliding on a nonce. */
public final class SessionKeys {

    public static final int KEY_BYTES = 32, IV_PREFIX_BYTES = 4;

    final SecretKey hostToViewer, viewerToHost;
    final byte[] hostToViewerIv, viewerToHostIv;

    SessionKeys(byte[] h2v, byte[] v2h) {
        hostToViewer = new SecretKeySpec(h2v, 0, KEY_BYTES, "AES");
        hostToViewerIv = Arrays.copyOfRange(h2v, KEY_BYTES, KEY_BYTES + IV_PREFIX_BYTES);
        viewerToHost = new SecretKeySpec(v2h, 0, KEY_BYTES, "AES");
        viewerToHostIv = Arrays.copyOfRange(v2h, KEY_BYTES, KEY_BYTES + IV_PREFIX_BYTES);
    }

    public static SessionKeys from(byte[] sharedSecret, String sessionId) {
        var salt = ("cfucli/v1/" + sessionId).getBytes(StandardCharsets.UTF_8);
        var n = KEY_BYTES + IV_PREFIX_BYTES;
        return new SessionKeys(
                Hkdf.derive(sharedSecret, salt, Direction.HOST_TO_VIEWER.info(), n),
                Hkdf.derive(sharedSecret, salt, Direction.VIEWER_TO_HOST.info(), n));
    }

    public SecretKey key(Direction d) {
        return d == Direction.HOST_TO_VIEWER ? hostToViewer : viewerToHost;
    }

    public byte[] ivPrefix(Direction d) {
        return d == Direction.HOST_TO_VIEWER ? hostToViewerIv : viewerToHostIv;
    }
}
