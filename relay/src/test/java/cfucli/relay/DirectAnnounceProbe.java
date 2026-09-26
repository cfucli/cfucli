package cfucli.relay;

import module java.base;

/** What {@link cfucli.node.HostSession#start} does with a LAN relay, reproduced here without a
 *  PTY or the beat/watch threads a full host session carries - just the two things that are new:
 *  publishing candidates onto a REAL relay (Upstash, from the real settings.toml) under the same
 *  meta key a viewer reads for the host's public key, and a viewer-side open finding them there
 *  and connecting direct instead of settling for the relay.
 *  <p>
 *  Run with {@code mvn exec:java -Dexec.mainClass=cfucli.relay.DirectAnnounceProbe
 *  -Dexec.classpathScope=test -pl relay}. Cleans its throwaway session key off the relay when done. */
public final class DirectAnnounceProbe {

    public static void main(String[] args) throws Exception {
        var settings = SettingsStore.load();
        var sessionId = "d" + (100000000 + new Random().nextInt(900000000));
        System.out.println("throwaway session id: " + sessionId);

        var lan = LanRelay.startQuietly();
        if (lan == null) throw new IllegalStateException("LanRelay did not start on this machine");
        var hostRelay = TransportFactory.open(settings);
        System.out.println("host's relay: " + hostRelay.description());
        try {
            var hosts = DirectCandidates.localAddresses();
            DirectCandidates.announce(hostRelay.transport(), sessionId, hosts, lan.port(), lan.token());
            System.out.println("announced " + hosts + " port=" + lan.port());

            // A separate, fresh relay open - exactly what a viewer on another machine does; not
            // the same transport instance the host is holding.
            var viewerRelay = TransportFactory.open(settings);
            try {
                var meta = viewerRelay.transport().getMeta(Channels.meta(sessionId));
                var advertised = DirectCandidates.read(meta);
                System.out.println("read back: " + advertised);
                if (advertised == null) throw new IllegalStateException("nothing came back from the relay meta");
                if (!advertised.hosts().equals(hosts)) throw new IllegalStateException("hosts did not round-trip");

                var choice = TransportFactory.direct(settings, sessionId, viewerRelay);
                System.out.println("viewer chose: " + choice.description() + "  notes=" + choice.notes());
                if (!choice.transport().direct()) throw new IllegalStateException("expected direct()==true");
                if (choice.transport().local()) throw new IllegalStateException("must not be local() across machines");
                if (choice.transport() == viewerRelay.transport())
                    throw new IllegalStateException("did not actually switch off the relay transport");
                choice.transport().close();
                System.out.println("OK");
            } finally {
                if (viewerRelay.transport() != hostRelay.transport()) {
                    try {
                        viewerRelay.transport().close();
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        } finally {
            Sessions.purge(hostRelay.transport(), sessionId);
            hostRelay.transport().close();
            lan.close();
        }
    }

    private DirectAnnounceProbe() {}
}
