package cfucli.relay;

import module java.base;
import module java.net.http;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;

/** The fallback, for networks that pass 443 and nothing else. Upstash's REST API has no blocking
 *  read, so this polls - fast right after traffic, backing off to {@link #IDLE_POLL} when the
 *  session goes quiet. That backoff is not cosmetic: a flat 250ms poll would spend the whole free
 *  monthly command allowance in about three days of idling. */
public final class RestTransport implements RelayTransport {

    public static final Duration BUSY_POLL = Duration.ofMillis(150), IDLE_POLL = Duration.ofSeconds(3);
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    final HttpClient http;
    final URI endpoint;
    final String token;
    final ObjectMapper json = new ObjectMapper();

    RestTransport(HttpClient http, URI endpoint, String token) {
        this.http = http;
        this.endpoint = endpoint;
        this.token = token;
    }

    public static RestTransport open(String restUrl, String restToken) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new RestTransport(http, URI.create(restUrl.endsWith("/") ? restUrl : restUrl + "/"), restToken);
    }

    @Override
    public String name() {
        return "rest";
    }

    @Override
    public String append(String stream, byte[] payload, long maxLen) {
        var r = command("XADD", stream, "MAXLEN", "~", Long.toString(maxLen), "*", "d",
                Base64.getEncoder().encodeToString(payload));
        return r.asText();
    }

    @Override
    public List<StreamRecord> read(String stream, String fromId, int count, Duration block) {
        var deadline = System.nanoTime() + (block == null ? 0 : block.toNanos());
        var wait = BUSY_POLL;
        while (true) {
            var found = readOnce(stream, fromId, count);
            if (!found.isEmpty() || System.nanoTime() >= deadline) return found;
            sleep(wait);
            wait = wait.multipliedBy(2).compareTo(IDLE_POLL) > 0 ? IDLE_POLL : wait.multipliedBy(2);
        }
    }

    @Override
    public String lastId(String stream) {
        var r = command("XREVRANGE", stream, "+", "-", "COUNT", "1");
        if (r == null || r.isNull() || !r.isArray() || r.isEmpty()) return RelayTransport.FIRST_ID;
        return r.get(0).get(0).asText(RelayTransport.FIRST_ID);
    }

    List<StreamRecord> readOnce(String stream, String fromId, int count) {
        var r = command("XREAD", "COUNT", Integer.toString(count), "STREAMS", stream, fromId);
        if (r == null || r.isNull()) return List.of();
        var entries = r.isArray() ? r.get(0).get(1) : r.fields().next().getValue();
        if (entries == null || !entries.isArray()) return List.of();
        var out = new ArrayList<StreamRecord>(entries.size());
        for (var e : entries) {
            var id = e.get(0).asText();
            var fields = e.get(1);
            for (var i = 0; i + 1 < fields.size(); i += 2) {
                if ("d".equals(fields.get(i).asText())) {
                    out.add(new StreamRecord(id, Base64.getDecoder().decode(fields.get(i + 1).asText())));
                }
            }
        }
        return out;
    }

    @Override
    public void putMeta(String key, Map<String, String> fields, Duration ttl) {
        var args = new ArrayList<String>();
        args.add("HSET");
        args.add(key);
        fields.forEach((k, v) -> {
            args.add(k);
            args.add(v);
        });
        command(args.toArray(String[]::new));
        if (ttl != null) touch(key, ttl);
    }

    @Override
    public Map<String, String> getMeta(String key) {
        var r = command("HGETALL", key);
        var out = new LinkedHashMap<String, String>();
        if (r == null || r.isNull()) return out;
        if (r.isObject()) {
            r.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
        } else {
            for (var i = 0; i + 1 < r.size(); i += 2) out.put(r.get(i).asText(), r.get(i + 1).asText());
        }
        return out;
    }

    @Override
    public void touch(String key, Duration ttl) {
        command("EXPIRE", key, Long.toString(Math.max(1, ttl.toSeconds())));
    }

    @Override
    public void delete(String... keys) {
        if (keys.length == 0) return;
        var args = new ArrayList<String>();
        args.add("DEL");
        args.addAll(List.of(keys));
        command(args.toArray(String[]::new));
    }

    @Override
    public void ping() {
        var pong = command("PING");
        if (pong == null || !"PONG".equalsIgnoreCase(pong.asText())) throw new IllegalStateException("PING answered " + pong);
    }

    JsonNode command(String... argv) {
        try {
            var body = json.createArrayNode();
            for (var a : argv) body.add(a);
            var req = HttpRequest.newBuilder(endpoint)
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var node = json.readTree(res.body());
            if (node.has("error")) throw new IllegalStateException("upstash " + argv[0] + ": " + node.get("error").asText());
            if (res.statusCode() / 100 != 2) throw new IllegalStateException("upstash HTTP " + res.statusCode() + " on " + argv[0]);
            return node.get("result");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    @Override
    public void close() {
        http.close();
    }
}
