package cfucli.relay;

public enum Role {
    HOST, VIEWER;

    public Direction outbound() {
        return this == HOST ? Direction.HOST_TO_VIEWER : Direction.VIEWER_TO_HOST;
    }

    public Direction inbound() {
        return outbound().opposite();
    }

    public String wire() {
        return name().toLowerCase();
    }

    public static Role of(String s) {
        for (var r : values()) if (r.name().equalsIgnoreCase(s)) return r;
        throw new IllegalArgumentException("unknown role " + s);
    }
}
