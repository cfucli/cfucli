package cfucli.relay;

import module java.base;

public record SessionStatus(String sessionId, SessionState state, Instant startedAt, Instant lastBeat,
                            String hostName, String shell, boolean locked, boolean viewOnly, String detail) {

    public Duration sinceBeat() {
        return lastBeat == null ? null : Duration.between(lastBeat, Instant.now());
    }

    public static SessionStatus none(String sessionId) {
        return new SessionStatus(sessionId, SessionState.NO_SESSION, null, null, null, null, false, false,
                "no session with that id is advertised on the relay");
    }
}
