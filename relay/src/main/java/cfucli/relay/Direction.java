package cfucli.relay;

import module java.base;

public enum Direction {
    HOST_TO_VIEWER("h2v"), VIEWER_TO_HOST("v2h");

    public final String wire;

    Direction(String wire) {
        this.wire = wire;
    }

    public Direction opposite() {
        return this == HOST_TO_VIEWER ? VIEWER_TO_HOST : HOST_TO_VIEWER;
    }

    public byte[] info() {
        return ("cfucli/v1/" + wire).getBytes(StandardCharsets.UTF_8);
    }

    public static Direction of(String wire) {
        for (var d : values()) if (d.wire.equals(wire)) return d;
        throw new IllegalArgumentException("unknown direction " + wire);
    }
}
