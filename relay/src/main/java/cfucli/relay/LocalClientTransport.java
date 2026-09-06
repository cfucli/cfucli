package cfucli.relay;

import module java.base;

/** The end next door. Same interface, same encrypted frames, same handshake - the only thing that
 *  changes is that the round trip is a loopback socket instead of a datacentre, which takes it
 *  from tens of milliseconds and a metered command to microseconds and nothing. */
public final class LocalClientTransport implements RelayTransport {

    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(10), READ_SLACK = Duration.ofSeconds(5);
    static final int POOL = 4;

    final LocalEndpoint endpoint;
    final Deque<LocalConn> pool = new ArrayDeque<>();

    volatile boolean closed;

    LocalClientTransport(LocalEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public static LocalClientTransport connect(LocalEndpoint endpoint) {
        var t = new LocalClientTransport(endpoint);
        t.ping();
        return t;
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public boolean local() {
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
        var req = LocalOps.request("append", endpoint.token())
                .put("stream", stream)
                .put("payload", LocalWire.b64(payload == null ? new byte[0] : payload))
                .put("maxLen", maxLen);
        return call(req, CALL_TIMEOUT).path("id").asText();
    }

    @Override
    public List<StreamRecord> read(String stream, String fromId, int count, Duration block) {
        var blockMs = block == null ? 0 : block.toMillis();
        var req = LocalOps.request("read", endpoint.token())
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
        return call(LocalOps.request("lastId", endpoint.token()).put("stream", stream), CALL_TIMEOUT)
                .path("id").asText(FIRST_ID);
    }

    @Override
    public void putMeta(String key, Map<String, String> fields, Duration ttl) {
        var req = LocalOps.request("putMeta", endpoint.token()).put("key", key);
        var f = req.putObject("fields");
        fields.forEach(f::put);
        call(req, CALL_TIMEOUT);
    }

    @Override
    public Map<String, String> getMeta(String key) {
        var res = call(LocalOps.request("getMeta", endpoint.token()).put("key", key), CALL_TIMEOUT);
        return LocalWire.fields(res.get("fields"));
    }

    @Override
    public void touch(String key, Duration ttl) {
    }

    @Override
    public void delete(String... keys) {
        var req = LocalOps.request("delete", endpoint.token());
        var arr = req.putArray("keys");
        for (var k : keys) arr.add(k);
        call(req, CALL_TIMEOUT);
    }

    @Override
    public void ping() {
        call(LocalOps.request("ping", endpoint.token()), Duration.ofSeconds(3));
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
        return LocalConn.open(endpoint.port());
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
