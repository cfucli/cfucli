package cfucli.relay;

import module java.base;

/** Native first, REST second, and it says which one it got and why the other was not used.
 *  A transport that silently degrades is the failure mode the originating project lost most time
 *  to, so the reason string is part of the result, not a log line nobody reads. */
public final class TransportFactory {

    public static final Duration MAX_BLOCK = Duration.ofSeconds(25);

    /** What a viewer should use to reach one particular session. A session hosted on this machine
     *  is answered from next door - no relay, no cost, no round trip to a datacentre for a
     *  keystroke that has ten inches to travel. */
    public static TransportChoice forViewer(Settings s, String sessionId) {
        var endpoint = LocalEndpoint.read(sessionId).filter(LocalEndpoint::processAlive);
        if (endpoint.isPresent()) {
            try {
                return new TransportChoice(LocalClientTransport.connect(endpoint.get()), "local loopback",
                        List.of("session " + sessionId + " is hosted on this machine by pid " + endpoint.get().pid()
                                + " - the relay is not involved"));
            } catch (RuntimeException e) {
                var choice = open(s);
                var notes = new ArrayList<>(choice.notes());
                notes.add("a local endpoint for " + sessionId + " exists but did not answer: " + rootMessage(e));
                return direct(s, sessionId, new TransportChoice(choice.transport(), choice.description(),
                        List.copyOf(notes)));
            }
        }
        return direct(s, sessionId, open(s));
    }

    /** The host may have advertised a LAN (or other direct) address on the very meta hash the
     *  relay was just opened to read - a viewer arriving from anywhere in the same network is the
     *  case this is for, and it costs nothing beyond one extra {@code getMeta} to look. Falls
     *  straight back to the relay when there is nothing advertised, nothing answers, or the
     *  setting says not to bother; the relay is the one path that always works, so failing to
     *  improve on it is not itself a failure. */
    public static TransportChoice direct(Settings s, String sessionId, TransportChoice relay) {
        var notes = new ArrayList<>(relay.notes());
        if (Boolean.FALSE.equals(s.lanDirect())) return relay;
        DirectCandidates.Advertised advertised;
        try {
            advertised = DirectCandidates.read(relay.transport().getMeta(Channels.meta(sessionId)));
        } catch (RuntimeException e) {
            return relay;
        }
        if (advertised == null) return relay;
        var found = DirectCandidates.connect(advertised);
        if (found.isEmpty()) {
            notes.add(advertised.hosts().size() + " direct address(es) were advertised but none answered - using "
                       + relay.description());
            return new TransportChoice(relay.transport(), relay.description(), List.copyOf(notes));
        }
        var lan = found.get();
        notes.add("connected directly to the host over " + lan.name() + " - the relay was not needed");
        relay.transport().close();
        return new TransportChoice(lan, lan.local() ? "local loopback" : "LAN direct (no relay)", List.copyOf(notes));
    }

    public static TransportChoice open(Settings s) {
        var pref = TransportPreference.of(s.transportPreference());
        var notes = new ArrayList<String>();
        if (pref != TransportPreference.REST_ONLY && has(s.redisUrl())) {
            var t = (LettuceTransport) null;
            try {
                t = LettuceTransport.open(s.redisUrl(), MAX_BLOCK);
                t.ping();
                return new TransportChoice(t, "native Redis over TLS", List.copyOf(notes));
            } catch (RuntimeException e) {
                if (t != null) t.close();
                notes.add("native unavailable: " + rootMessage(e));
            }
        } else if (pref == TransportPreference.REST_ONLY) {
            notes.add("native skipped: transportPreference is REST_ONLY");
        } else {
            notes.add("native skipped: no REDIS_URL in " + SettingsStore.FILE);
        }

        if (pref == TransportPreference.NATIVE_ONLY) {
            throw new IllegalStateException("native transport required but unavailable - " + String.join("; ", notes));
        }
        if (!has(s.restUrl()) || !has(s.restToken())) {
            throw new IllegalStateException("no usable transport - " + String.join("; ", notes)
                    + "; and no UPSTASH_REDIS_REST_URL/TOKEN in " + SettingsStore.FILE);
        }
        var rest = RestTransport.open(s.restUrl(), s.restToken());
        try {
            rest.ping();
        } catch (RuntimeException e) {
            rest.close();
            throw new IllegalStateException("no usable transport - " + String.join("; ", notes)
                    + "; REST also failed: " + rootMessage(e), e);
        }
        return new TransportChoice(rest, "Upstash REST over HTTPS", List.copyOf(notes));
    }

    static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    static String rootMessage(Throwable t) {
        var c = t;
        while (c.getCause() != null) c = c.getCause();
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }

    private TransportFactory() {}
}
