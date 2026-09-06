package cfucli.relay;

import module java.base;

/** A session's relay when both ends are on this machine: the store, the loopback door onto it,
 *  and the file that tells the other end where to knock. Owned by the host; the viewer only ever
 *  sees {@link LocalClientTransport}. */
public final class LocalRelay implements AutoCloseable {

    final String sessionId;
    final LocalStore store;
    final LocalServer server;
    final LocalEndpoint endpoint;

    LocalRelay(String sessionId, LocalStore store, LocalServer server, LocalEndpoint endpoint) {
        this.sessionId = sessionId;
        this.store = store;
        this.server = server;
        this.endpoint = endpoint;
    }

    public static LocalRelay start(String sessionId, String node) {
        var store = new LocalStore();
        var token = LocalEndpoint.newToken();
        var server = LocalServer.start(store, token);
        var endpoint = new LocalEndpoint(sessionId, server.port(), ProcessHandle.current().pid(),
                node, System.currentTimeMillis(), token);
        endpoint.write();
        return new LocalRelay(sessionId, store, server, endpoint);
    }

    public String sessionId() {
        return sessionId;
    }

    public LocalEndpoint endpoint() {
        return endpoint;
    }

    public int port() {
        return server.port();
    }

    /** The host's handle on its own store - in-process, no socket. */
    public RelayTransport transport() {
        return new LocalTransport(store);
    }

    @Override
    public void close() {
        server.close();
        LocalEndpoint.remove(sessionId);
    }
}
