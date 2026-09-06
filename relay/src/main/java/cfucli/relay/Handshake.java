package cfucli.relay;

import module java.base;

/** X25519 between the two ends, with the one-time password authenticating both public keys.
 *  <p>
 *  A fresh agreement runs every time a viewer joins, so each visit gets its own traffic key and a
 *  password recovered afterwards will not open a recording of the relay. The cost of that choice
 *  is that old stream entries cannot be replayed to a rejoining viewer - which is fine, because
 *  history is held locally by each end's recorder, and a rejoining viewer is caught up with a
 *  STATE frame carrying the current screen instead. */
public final class Handshake {

    public static final Duration POLL = Duration.ofMillis(400);

    public static void announce(RelayTransport t, HostIdentity id, String hostName, String shell) {
        var enc = Base64.getEncoder();
        var now = Instant.now();
        var fields = new LinkedHashMap<String, String>();
        fields.put(Meta.VERSION, Meta.PROTOCOL_VERSION);
        fields.put(Meta.HOST_PUBLIC_KEY, enc.encodeToString(id.publicKey()));
        fields.put(Meta.HOST_TAG, enc.encodeToString(PairingSecret.tag(id.pairingSecret(), "host", id.publicKey())));
        fields.put(Meta.STARTED_AT, Meta.stamp(now));
        fields.put(Meta.LAST_BEAT, Meta.stamp(now));
        fields.put(Meta.HOST_NAME, hostName == null ? "" : hostName);
        fields.put(Meta.SHELL, shell == null ? "" : shell);
        t.putMeta(Channels.meta(id.sessionId()), fields, Meta.TTL);
    }

    public static void beat(RelayTransport t, String sessionId) {
        t.putMeta(Channels.meta(sessionId), Map.of(Meta.LAST_BEAT, Meta.stamp(Instant.now())), Meta.TTL);
    }

    /** One look at the session hash: a viewer whose public key differs from
     *  {@code previousViewerKey} and whose tag is good, or null.
     *  <p>
     *  One look rather than a loop, because a host may be listening on more than one transport at
     *  once and each has its own idea of how often it is worth asking - free and immediate on the
     *  loopback, a billable command on the relay. The caller owns the rhythm. */
    public static ViewerArrival pollViewer(RelayTransport t, HostIdentity id, String previousViewerKey) {
        var meta = t.getMeta(Channels.meta(id.sessionId()));
        var vpub = meta.get(Meta.VIEWER_PUBLIC_KEY);
        var vtag = meta.get(Meta.VIEWER_TAG);
        if (vpub == null || vtag == null || vpub.equals(previousViewerKey)) return null;
        var raw = Base64.getDecoder().decode(vpub);
        if (!PairingSecret.verify(id.pairingSecret(), "viewer", raw, Base64.getDecoder().decode(vtag))) {
            throw new SecurityException("a viewer presented a key that does not match this session's password");
        }
        var keys = SessionKeys.from(KeyExchange.agree(id.keyPair().getPrivate(), KeyExchange.decode(raw)), id.sessionId());
        return new ViewerArrival(keys, vpub);
    }

    /** Blocks until a viewer whose public key differs from {@code previousViewerKey} presents
     *  itself with a valid tag. Returns null on timeout. */
    public static ViewerArrival awaitViewer(RelayTransport t, HostIdentity id, String previousViewerKey, Duration timeout) {
        var deadline = Instant.now().plus(timeout);
        for (;;) {
            var arrival = pollViewer(t, id, previousViewerKey);
            if (arrival != null) return arrival;
            if (!Instant.now().isBefore(deadline)) return null;
            RestTransport.sleep(POLL);
        }
    }

    public static SessionKeys join(RelayTransport t, String sessionId, String password) {
        var status = Sessions.status(t, sessionId);
        if (status.state() == SessionState.NO_SESSION) throw new NoSuchElementException(status.detail());
        if (status.state() == SessionState.ENDED) throw new IllegalStateException(status.detail());

        var meta = t.getMeta(Channels.meta(sessionId));
        var hpub = meta.get(Meta.HOST_PUBLIC_KEY);
        var htag = meta.get(Meta.HOST_TAG);
        if (hpub == null || htag == null) throw new IllegalStateException("session " + sessionId + " has not published a host key yet");

        var pairing = PairingSecret.derive(password, sessionId);
        var dec = Base64.getDecoder();
        var hostKey = dec.decode(hpub);
        if (!PairingSecret.verify(pairing, "host", hostKey, dec.decode(htag))) {
            throw new SecurityException("wrong one-time password for session " + Ids.prettySessionId(sessionId));
        }

        var mine = KeyExchange.newKeyPair();
        var mineEncoded = KeyExchange.encode(mine.getPublic());
        var enc = Base64.getEncoder();
        t.putMeta(Channels.meta(sessionId), Map.of(
                Meta.VIEWER_PUBLIC_KEY, enc.encodeToString(mineEncoded),
                Meta.VIEWER_TAG, enc.encodeToString(PairingSecret.tag(pairing, "viewer", mineEncoded))), Meta.TTL);

        return SessionKeys.from(KeyExchange.agree(mine.getPrivate(), KeyExchange.decode(hostKey)), sessionId);
    }

    private Handshake() {}
}
