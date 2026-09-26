package cfucli.relay;

import module java.base;

/** The end next door - on this machine over the loopback, or on the LAN at whichever address
 *  answered. Same interface, same encrypted frames, same handshake either way - the only thing
 *  that changes is that the round trip is a private socket instead of a datacentre, which takes
 *  it from tens of milliseconds and a metered command to microseconds (loopback) or a few
 *  milliseconds (LAN) and nothing. */
public final class LocalClientTransport implements RelayTransport {

    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(10), READ_SLACK = Duration.ofSeconds(5);

    /** Kept short because a LAN candidate is raced against however many others the host
     *  advertised - one address nobody is listening on, or one this machine cannot even route to,
     *  must not hold the race up for the OS's own much longer default (which on an unreachable
     *  host can run into several seconds). */
    public static final int LAN_CONNECT_TIMEOUT_MS = 400;

    static final int POOL = 4, LOOPBACK_CONNECT_TIMEOUT_MS = 3000;

    final InetSocketAddress address;
    final String token;
    final boolean loopback;
    final int connectTimeoutMs;
    final Deque<LocalConn> pool = new ArrayDeque<>();

    volatile boolean closed;

    LocalClientTransport(InetSocketAddress address, String token, int connectTimeoutMs) {
        this.address = address;
        this.token = token;
        this.loopback = address.getAddress() != null && address.getAddress().isLoopbackAddress();
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /** The same-machine case: a session hosted next door, found by its port file. */
    public static LocalClientTransport connect(LocalEndpoint endpoint) {
        var t = new LocalClientTransport(new InetSocketAddress(InetAddress.getLoopbackAddress(), endpoint.port()),
                endpoint.token(), LOOPBACK_CONNECT_TIMEOUT_MS);
        t.ping();
        return t;
    }

    /** A LAN (or other direct) candidate the host advertised over the relay's own meta - an
     *  address and port that are not this machine's, guarded by the token the host minted for
     *  this session's direct listener. Throws if nothing answers within {@link #LAN_CONNECT_TIMEOUT_MS}. */
    public static LocalClientTransport connect(InetSocketAddress address, String token) {
        var t = new LocalClientTransport(address, token, LAN_CONNECT_TIMEOUT_MS);
        t.ping();
        return t;
    }

    @Override
    public String name() {
        return loopback ? "local" : "lan";
    }

    @Override
    public boolean local() {
        return loopback;
    }

    @Override
    public boolean direct() {
        return true;
    }

    @Override
    public Duration outputWindow() {
        return LocalTransport.OUTPUT_WINDOW;
    }

    @Override
    public Duration pollInterval() {
        return LocalTransport.POLL;
    }

    @Override
    public String append(String stream, byte[] payload, long maxLen) {
        var req = LocalOps.request("append", token)
                .put("stream", stream)
                .put("payload", LocalWire.b64(payload == null ? new byte[0] : payload))
                .put("maxLen", maxLen);
        return call(req, CALL_TIMEOUT).path("id").asText();
    }

    @Override
    public List<StreamRecord> read(String stream, String fromId, int count, Duration block) {
        var blockMs = block == null ? 0 : block.toMillis();
        var req = LocalOps.request("read", token)
                .put("stream", stream)
                .put("from", fromId == null ? FIRST_ID : fromId)
                .put("count", count)
                .put("blockMs", blockMs);
        var out = new ArrayList<StreamRecord>();
        for (var r : call(req, Duration.ofMillis(blockMs).plus(READ_SLACK)).path("records")) {
            out.add(new StreamRecord(r.path("id").asText(), LocalWire.unb64(r.path("payload").asText())));
        }
        return out;
    }

    @Override
    public String lastId(String stream) {
        return call(LocalOps.request("lastId", token).put("stream", stream), CALL_TIMEOUT)
                .path("id").asText(FIRST_ID);
    }

    @Override
    public void putMeta(String key, Map<String, String> fields, Duration ttl) {
        var req = LocalOps.request("putMeta", token).put("key", key);
        var f = req.putObject("fields");
        fields.forEach(f::put);
        call(req, CALL_TIMEOUT);
    }

    @Override
    public Map<String, String> getMeta(String key) {
        var res = call(LocalOps.request("getMeta", token).put("key", key), CALL_TIMEOUT);
        return LocalWire.fields(res.get("fields"));
    }

    @Override
    public void touch(String key, Duration ttl) {
    }

    @Override
    public void delete(String... keys) {
        var req = LocalOps.request("delete", token);
        var arr = req.putArray("keys");
        for (var k : keys) arr.add(k);
        call(req, CALL_TIMEOUT);
    }

    @Override
    public void ping() {
        call(LocalOps.request("ping", token), Duration.ofMillis(Math.max(connectTimeoutMs, 3000)));
    }

    com.fasterxml.jackson.databind.JsonNode call(com.fasterxml.jackson.databind.node.ObjectNode req, Duration timeout) {
        if (closed) throw new IllegalStateException("this local transport is closed");
        var conn = borrow();
        try {
            var res = conn.call(req, timeout);
            give(conn);
            return res;
        } catch (RuntimeException e) {
            conn.close();
            throw e;
        }
    }

    LocalConn borrow() {
        synchronized (pool) {
            while (!pool.isEmpty()) {
                var c = pool.poll();
                if (c.alive()) return c;
                c.close();
            }
        }
        return LocalConn.open(address, connectTimeoutMs);
    }

    void give(LocalConn c) {
        synchronized (pool) {
            if (!closed && pool.size() < POOL) {
                pool.push(c);
                return;
            }
        }
        c.close();
    }

    @Override
    public void close() {
        closed = true;
        synchronized (pool) {
            pool.forEach(LocalConn::close);
            pool.clear();
        }
    }
}
