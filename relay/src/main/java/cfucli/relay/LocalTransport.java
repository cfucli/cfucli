package cfucli.relay;

import module java.base;

/** The host's own view of a {@link LocalStore}: no socket, no serialisation, no copy. The end
 *  that owns the store talks to it directly and the end next door comes in over a socket through
 *  {@link LocalClientTransport} - the loopback for a same-machine viewer, a LAN address for one
 *  next door on the network.
 *  <p>
 *  {@code local} is false for the LAN case: two different machines have no filesystem to share,
 *  whatever the wire between them costs. See {@link RelayTransport#local()}. */
public final class LocalTransport implements RelayTransport {

    public static final Duration OUTPUT_WINDOW = Duration.ZERO, POLL = Duration.ofMillis(120);

    final LocalStore store;
    final boolean local;

    public LocalTransport(LocalStore store) {
        this(store, true);
    }

    public LocalTransport(LocalStore store, boolean local) {
        this.store = store;
        this.local = local;
    }

    @Override
    public String name() {
        return local ? "local" : "lan";
    }

    @Override
    public boolean local() {
        return local;
    }

    @Override
    public boolean direct() {
        return true;
    }

    /** Nothing is being metered and nothing crosses a network, so output goes out as it is
     *  produced. This is the whole point of the local path: the coalescing window exists to
     *  protect a monthly command allowance that no longer applies. */
    @Override
    public Duration outputWindow() {
        return OUTPUT_WINDOW;
    }

    @Override
    public Duration pollInterval() {
        return POLL;
    }

    @Override
    public String append(String stream, byte[] payload, long maxLen) {
        return store.append(stream, payload, maxLen);
    }

    @Override
    public List<StreamRecord> read(String stream, String fromId, int count, Duration block) {
        return store.read(stream, fromId, count, block);
    }

    @Override
    public String lastId(String stream) {
        return store.lastId(stream);
    }

    @Override
    public void putMeta(String key, Map<String, String> fields, Duration ttl) {
        store.putMeta(key, fields, ttl);
    }

    @Override
    public Map<String, String> getMeta(String key) {
        return store.getMeta(key);
    }

    @Override
    public void touch(String key, Duration ttl) {
    }

    @Override
    public void delete(String... keys) {
        store.delete(keys);
    }

    @Override
    public void ping() {
    }

    @Override
    public void close() {
    }
}
