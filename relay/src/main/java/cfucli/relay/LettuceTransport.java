package cfucli.relay;

import module java.base;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.resource.ClientResources;
import io.netty.resolver.DefaultAddressResolverGroup;

/** The preferred transport: the real Redis protocol over TLS on 6379, which gives a blocking
 *  XREAD and so costs one command per wake-up instead of one per poll.
 *  <p>
 *  Writes get their own connection, and every stream being followed gets one of its own too. A
 *  blocking XREAD occupies its connection for as long as it blocks - Redis will not begin the
 *  next command on that connection until it returns - so two followers sharing one connection
 *  starve each other, and a keystroke queued behind a 20-second read simply never leaves. */
public final class LettuceTransport implements RelayTransport {

    static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10), READ_HEADROOM = Duration.ofSeconds(10);

    final RedisClient client;
    final ClientResources resources;
    final StatefulRedisConnection<String, String> writeConn;
    final Duration readTimeout;
    final Map<String, StatefulRedisConnection<String, String>> readers = new ConcurrentHashMap<>();

    LettuceTransport(RedisClient client, ClientResources resources,
                     StatefulRedisConnection<String, String> writeConn, Duration readTimeout) {
        this.client = client;
        this.resources = resources;
        this.writeConn = writeConn;
        this.readTimeout = readTimeout;
    }

    StatefulRedisConnection<String, String> reader(String stream) {
        return readers.computeIfAbsent(stream, s -> {
            var c = client.connect();
            c.setTimeout(readTimeout);
            return c;
        });
    }

    public static LettuceTransport open(String redisUrl, Duration maxBlock) {
        var uri = RedisURI.create(redisUrl);
        uri.setTimeout(WRITE_TIMEOUT);
        // Netty resolves DNS itself by default, straight to a public resolver over UDP 53. Plenty
        // of networks drop that, and the symptom is a name-resolution timeout on a host the OS
        // resolves perfectly well. Hand name resolution back to the JDK, which uses the OS.
        var resources = ClientResources.builder()
                .addressResolverGroup(DefaultAddressResolverGroup.INSTANCE)
                .build();
        var client = RedisClient.create(resources, uri);
        client.setOptions(ClientOptions.builder().autoReconnect(true).build());
        var write = client.connect();
        write.setTimeout(WRITE_TIMEOUT);
        return new LettuceTransport(client, resources, write, maxBlock.plus(READ_HEADROOM));
    }

    @Override
    public String name() {
        return "native";
    }

    @Override
    public String append(String stream, byte[] payload, long maxLen) {
        var args = XAddArgs.Builder.maxlen(maxLen).approximateTrimming();
        return writeConn.sync().xadd(stream, args, Map.of("d", Base64.getEncoder().encodeToString(payload)));
    }

    @Override
    public List<StreamRecord> read(String stream, String fromId, int count, Duration block) {
        var args = XReadArgs.Builder.count(count);
        if (block != null && !block.isZero()) args = args.block(block);
        var messages = reader(stream).sync().xread(args, XReadArgs.StreamOffset.from(stream, fromId));
        if (messages == null || messages.isEmpty()) return List.of();
        return messages.stream().map(LettuceTransport::toRecord).filter(Objects::nonNull).toList();
    }

    @Override
    public String lastId(String stream) {
        var last = writeConn.sync().xrevrange(stream, Range.unbounded(), Limit.from(1));
        return last == null || last.isEmpty() ? RelayTransport.FIRST_ID : last.get(0).getId();
    }

    static StreamRecord toRecord(StreamMessage<String, String> m) {
        var d = m.getBody() == null ? null : m.getBody().get("d");
        return d == null ? null : new StreamRecord(m.getId(), Base64.getDecoder().decode(d));
    }

    @Override
    public void putMeta(String key, Map<String, String> fields, Duration ttl) {
        var sync = writeConn.sync();
        sync.hset(key, fields);
        if (ttl != null) sync.expire(key, ttl);
    }

    @Override
    public Map<String, String> getMeta(String key) {
        var m = writeConn.sync().hgetall(key);
        return m == null ? Map.of() : m;
    }

    @Override
    public void touch(String key, Duration ttl) {
        writeConn.sync().expire(key, ttl);
    }

    @Override
    public void delete(String... keys) {
        if (keys.length > 0) writeConn.sync().del(keys);
    }

    @Override
    public void ping() {
        var pong = writeConn.sync().ping();
        if (!"PONG".equalsIgnoreCase(pong)) throw new IllegalStateException("PING answered " + pong);
    }

    @Override
    public void close() {
        readers.values().forEach(c -> closeQuietly(c::close));
        readers.clear();
        closeQuietly(writeConn::close);
        closeQuietly(client::shutdown);
        closeQuietly(resources::shutdown);
    }

    static void closeQuietly(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
        }
    }
}
