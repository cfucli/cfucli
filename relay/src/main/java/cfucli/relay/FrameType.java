package cfucli.relay;

/** Wire codes are explicit so the enum can be reordered without breaking a running session. */
public enum FrameType {
    HELLO(1), BYE(2), PING(3), PONG(4),
    OUTPUT(10), INPUT(11), RESIZE(12),
    EXEC_REQUEST(20), EXEC_STDOUT(21), EXEC_STDERR(22), EXEC_EXIT(23), EXEC_CANCEL(24),
    CONTROL(30), STATE(31),
    FILE_OFFER(40), FILE_CHUNK(41), FILE_DONE(42), FILE_REQUEST(43),
    ERROR(99);

    public final byte code;

    FrameType(int code) {
        this.code = (byte) code;
    }

    public static FrameType of(byte code) {
        for (var t : values()) if (t.code == code) return t;
        throw new IllegalArgumentException("unknown frame type " + code);
    }
}
