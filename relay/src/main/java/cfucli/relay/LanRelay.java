package cfucli.relay;

import module java.base;

/** A session's relay when the two ends might be on the same LAN: the store and a door onto it
 *  bound to every interface rather than just the loopback.
 *  <p>
 *  Unlike {@link LocalRelay} there is no discovery file - the file trick only works because both
 *  ends read the same filesystem, which is exactly what is not true here. Discovery instead rides
 *  the relay meta the host already publishes: {@link Handshake#announceDirect} adds this
 *  listener's candidate addresses, port and token onto the same hash a viewer reads to find the
 *  host's public key, so learning where to knock costs nothing beyond what the handshake was
 *  already going to cost. Opening this is opportunistic - a machine with no usable IPv4 address,
 *  or a port the OS refuses, still hosts perfectly well over the loopback and the relay alone. */
public final class LanRelay implements AutoCloseable {

    final LocalStore store;
    final LocalServer server;
    final String token;

    LanRelay(LocalStore store, LocalServer server, String token) {
        this.store = store;
        this.server = server;
        this.token = token;
    }

    /** Null rather than a thrown exception: LAN direct is a bonus, never a reason hosting a
     *  session should fail. */
    public static LanRelay startQuietly() {
        try {
            var store = new LocalStore();
            var token = LocalEndpoint.newToken();
            var server = LocalServer.start(store, token, true);
            return new LanRelay(store, server, token);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public int port() {
        return server.port();
    }

    public String token() {
        return token;
    }

    /** The host's own handle on its store - in-process, no socket, and {@code local() == false}:
     *  whichever viewer arrives here is on a different machine, so a file transfer still has a
     *  wire to cross even though the wire is free. */
    public RelayTransport transport() {
        return new LocalTransport(store, false);
    }

    @Override
    public void close() {
        server.close();
    }
}
