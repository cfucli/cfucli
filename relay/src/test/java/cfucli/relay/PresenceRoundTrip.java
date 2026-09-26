package cfucli.relay;

import module java.base;

/** Two identities, simulated in one process against the real relay: A heartbeats, B lists and
 *  sees A; B requests a connect from A; A polls and sees the request; A replies accepted; B
 *  waits and gets the reply. Everything a real two-machine "request instead of sharing an id and
 *  password" flow needs, minus the actual hosting - that part is {@code startHost}, already built
 *  and unchanged by any of this.
 *  <p>
 *  Run with {@code mvn exec:java -Dexec.mainClass=cfucli.relay.PresenceRoundTrip
 *  -Dexec.classpathScope=test -pl relay}. */
public final class PresenceRoundTrip {

    public static void main(String[] args) throws Exception {
        var settings = SettingsStore.load();
        var a = "probe-a-" + System.currentTimeMillis() % 100000;
        var b = "probe-b-" + System.currentTimeMillis() % 100000;
        System.out.println("identities: A=" + a + "  B=" + b);

        var relayA = TransportFactory.open(settings);
        var relayB = TransportFactory.open(settings);
        try {
            Presence.heartbeat(relayA.transport(), a, "machine-a");
            var seenByB = Presence.list(relayB.transport());
            System.out.println("B sees: " + seenByB);
            if (seenByB.stream().noneMatch(e -> e.identity().equals(a)))
                throw new IllegalStateException("B did not see A in the roster");

            var requestId = Presence.sendRequest(relayB.transport(), a, b, "machine-b");
            System.out.println("B requested A, requestId=" + requestId);

            var pending = Presence.pollRequests(relayA.transport(), a);
            System.out.println("A sees pending: " + pending);
            if (pending.stream().noneMatch(r -> r.requesterIdentity().equals(b) && r.requestId().equals(requestId)))
                throw new IllegalStateException("A did not see B's request");

            Presence.reply(relayA.transport(), b, requestId, new Presence.Reply(true, null, "123456789", "s3cr3t"));
            System.out.println("A replied accepted");

            var reply = Presence.awaitReply(relayB.transport(), b, requestId, Duration.ofSeconds(10));
            System.out.println("B received: " + reply);
            if (reply == null || !reply.accepted() || !"123456789".equals(reply.sessionId())
                || !"s3cr3t".equals(reply.password()))
                throw new IllegalStateException("reply did not round-trip correctly");

            // A second wait for the same request must not hang or double-answer - the reply key
            // was deleted on first receipt.
            var second = Presence.awaitReply(relayB.transport(), b, requestId, Duration.ofSeconds(2));
            System.out.println("second wait (expect null, already consumed): " + second);

            System.out.println("OK");
        } finally {
            relayA.transport().delete(Channels.request(a), Channels.reply(b, "leftover"));
            relayA.transport().close();
            relayB.transport().close();
        }
    }

    private PresenceRoundTrip() {}
}
