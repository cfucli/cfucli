package cfucli.relay;

import module java.base;

public final class Sessions {

    /** A host that has not beaten in three heartbeats is silent, not gone. */
    public static final int MISSED_BEATS = 3;

    public static SessionStatus status(RelayTransport t, String sessionId) {
        var meta = t.getMeta(Channels.meta(sessionId));
        if (meta.isEmpty()) return SessionStatus.none(sessionId);
        var beat = Meta.instant(meta, Meta.LAST_BEAT);
        var ended = Meta.flag(meta, Meta.ENDED);
        var stale = beat == null || Duration.between(beat, Instant.now()).compareTo(Meta.HEARTBEAT.multipliedBy(MISSED_BEATS)) > 0;
        var state = ended ? SessionState.ENDED : stale ? SessionState.HOST_SILENT : SessionState.HOST_RESPONDING;
        return new SessionStatus(sessionId, state,
                Meta.instant(meta, Meta.STARTED_AT), beat,
                meta.get(Meta.HOST_NAME), meta.get(Meta.SHELL),
                Meta.flag(meta, Meta.LOCKED), Meta.flag(meta, Meta.VIEW_ONLY),
                detail(state, beat));
    }

    static String detail(SessionState state, Instant beat) {
        return switch (state) {
            case NO_SESSION -> "no session with that id is advertised on the relay";
            case ENDED -> "the host ended this session";
            case HOST_SILENT -> beat == null
                    ? "session advertised but the host has never checked in"
                    : "session advertised but the host has not checked in for "
                      + Duration.between(beat, Instant.now()).toSeconds() + "s";
            case HOST_RESPONDING -> "host is checking in";
        };
    }

    public static void end(RelayTransport t, String sessionId) {
        t.putMeta(Channels.meta(sessionId), Map.of(Meta.ENDED, "1", Meta.LAST_BEAT, Meta.stamp(Instant.now())), Meta.TTL);
    }

    public static void purge(RelayTransport t, String sessionId) {
        t.delete(Channels.meta(sessionId),
                Channels.stream(sessionId, Direction.HOST_TO_VIEWER),
                Channels.stream(sessionId, Direction.VIEWER_TO_HOST));
    }

    private Sessions() {}
}
