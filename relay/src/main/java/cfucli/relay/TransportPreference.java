package cfucli.relay;

public enum TransportPreference {
    AUTO, NATIVE_ONLY, REST_ONLY;

    public static TransportPreference of(String s) {
        if (s == null || s.isBlank()) return AUTO;
        for (var v : values()) if (v.name().equalsIgnoreCase(s.trim())) return v;
        return AUTO;
    }
}
