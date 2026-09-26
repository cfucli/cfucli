package cfucli.relay;

import module java.base;

/** Who is around to be asked, and the request/reply that stands in for reading a session id and
 *  password out loud.
 *  <p>
 *  Three small hashes, the same shape {@link Handshake}/{@link Sessions} already use - flat
 *  fields, TTL'd, refreshed by whoever is still there:
 *  <ul>
 *  <li>{@code ucli:presence} - one shared roster. Field = identity name, value = a packed
 *  {@code node|epochMillis} string rather than nested JSON, because several different people's
 *  fields share the one key and {@link RelayTransport#putMeta} only carries a TTL for the whole
 *  key - a busy person's heartbeat would otherwise keep a machine that crashed hours ago looking
 *  live. Readers filter by the embedded timestamp instead of trusting the key's own TTL.
 *  <li>{@code ucli:req:<target>} - pending requests aimed at one identity, same reasoning: field =
 *  requester's identity, value = {@code requestId|fromNode|epochMillis}.
 *  <li>{@code ucli:reply:<requester>:<requestId>} - one key per request, so it needs none of that:
 *  ordinary named fields, exactly like a session's own meta.
 * </ul>
 *  Identity names are self-asserted, same as everything else on this relay - anyone holding the
 *  Upstash credentials could already write anything under {@code ucli:}. That is the existing
 *  trust boundary this rides on, not a new one: the people who can see a presence entry or send a
 *  request are exactly the people who could already forge a session announcement. */
public final class Presence {

    /** Longer than one heartbeat interval by a comfortable margin, so one missed tick under load
     *  does not flicker someone off the list. */
    public static final Duration STALE_AFTER = Duration.ofSeconds(90), HEARTBEAT_EVERY = Duration.ofSeconds(30);
    public static final Duration REQUEST_TTL = Duration.ofSeconds(90), REPLY_TTL = Duration.ofMinutes(3);
    public static final Duration POLL = Duration.ofSeconds(2);

    public record Entry(String identity, String node, Instant lastSeen) {
        public boolean stale() {
            return Duration.between(lastSeen, Instant.now()).compareTo(STALE_AFTER) > 0;
        }
    }

    public record IncomingRequest(String requesterIdentity, String requestId, String fromNode, Instant at) {}

    /** {@code sessionId}/{@code password} are set only when {@code accepted}. */
    public record Reply(boolean accepted, String reason, String sessionId, String password) {}

    public static void heartbeat(RelayTransport t, String identity, String node) {
        requireIdentity(identity);
        t.putMeta(Channels.presence(), Map.of(identity, pack(node, Instant.now())), STALE_AFTER.multipliedBy(2));
    }

    /** Only the fresh ones - a field left behind by a machine that crashed without a chance to say
     *  so is not filtered by the relay, so this end filters it instead. */
    public static List<Entry> list(RelayTransport t) {
        var meta = t.getMeta(Channels.presence());
        var out = new ArrayList<Entry>();
        meta.forEach((identity, packed) -> {
            var e = unpackEntry(identity, packed);
            if (e != null && !e.stale()) out.add(e);
        });
        out.sort(Comparator.comparing(Entry::identity));
        return out;
    }

    /** @return a request id the requester can wait on with {@link #awaitReply} */
    public static String sendRequest(RelayTransport t, String targetIdentity, String fromIdentity, String fromNode) {
        requireIdentity(targetIdentity);
        requireIdentity(fromIdentity);
        var requestId = newId();
        t.putMeta(Channels.request(targetIdentity), Map.of(fromIdentity, requestId + "|" + fromNode + "|"
                + Instant.now().toEpochMilli()), REQUEST_TTL);
        return requestId;
    }

    /** Every request aimed at {@code myIdentity} that is not stale. The caller is responsible for
     *  not acting on the same request id twice - see the node-side watcher, which remembers what
     *  it has already offered for exactly this reason. */
    public static List<IncomingRequest> pollRequests(RelayTransport t, String myIdentity) {
        var meta = t.getMeta(Channels.request(myIdentity));
        var out = new ArrayList<IncomingRequest>();
        meta.forEach((requesterIdentity, packed) -> {
            var parts = packed.split("\\|", 3);
            if (parts.length != 3) return;
            var at = epoch(parts[2]);
            if (at == null || Duration.between(at, Instant.now()).compareTo(REQUEST_TTL) > 0) return;
            out.add(new IncomingRequest(requesterIdentity, parts[0], parts[1], at));
        });
        return out;
    }

    public static void reply(RelayTransport t, String requesterIdentity, String requestId, Reply r) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("accepted", r.accepted() ? "1" : "0");
        if (r.reason() != null) fields.put("reason", r.reason());
        if (r.accepted()) {
            fields.put("sessionId", r.sessionId());
            fields.put("password", r.password());
        }
        t.putMeta(Channels.reply(requesterIdentity, requestId), fields, REPLY_TTL);
    }

    /** Blocks until the target answers or {@code timeout} runs out (null, meaning "nobody
     *  answered - they may be offline, or the request expired unseen"). */
    public static Reply awaitReply(RelayTransport t, String requesterIdentity, String requestId, Duration timeout) {
        var deadline = Instant.now().plus(timeout);
        var key = Channels.reply(requesterIdentity, requestId);
        for (;;) {
            var meta = t.getMeta(key);
            var accepted = meta.get("accepted");
            if (accepted != null) {
                t.delete(key);
                return "1".equals(accepted)
                        ? new Reply(true, null, meta.get("sessionId"), meta.get("password"))
                        : new Reply(false, meta.getOrDefault("reason", "declined"), null, null);
            }
            if (!Instant.now().isBefore(deadline)) return null;
            RestTransport.sleep(POLL);
        }
    }

    static String pack(String node, Instant at) {
        return (node == null ? "" : node) + "|" + at.toEpochMilli();
    }

    static Entry unpackEntry(String identity, String packed) {
        var parts = packed.split("\\|", 2);
        if (parts.length != 2) return null;
        var at = epoch(parts[1]);
        return at == null ? null : new Entry(identity, parts[0], at);
    }

    static Instant epoch(String s) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(s.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String newId() {
        var raw = new byte[9];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    static void requireIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalStateException("no identityName is set in " + SettingsStore.path()
                    + " - presence needs a name to be known by");
        }
    }

    private Presence() {}
}
