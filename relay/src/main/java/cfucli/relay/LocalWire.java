package cfucli.relay;

import module java.base;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** One JSON object per line, the same shape the node's own loopback protocol uses. Readable by
 *  eye when something is wrong, which is most of why it is not binary - the payloads inside it
 *  are encrypted anyway, so a binary framing would buy nothing but opacity. */
public final class LocalWire {

    public static final ObjectMapper JSON = new ObjectMapper();

    public static ObjectNode obj() {
        return JSON.createObjectNode();
    }

    public static String line(JsonNode n) {
        try {
            return JSON.writeValueAsString(n);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonNode parse(String line) {
        try {
            return JSON.readTree(line);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String str(JsonNode n, String key, String fallback) {
        var v = n == null ? null : n.get(key);
        return v == null || v.isNull() ? fallback : v.asText();
    }

    public static long num(JsonNode n, String key, long fallback) {
        var v = n == null ? null : n.get(key);
        return v == null || v.isNull() ? fallback : v.asLong();
    }

    public static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] unb64(String s) {
        return s == null || s.isEmpty() ? new byte[0] : Base64.getDecoder().decode(s);
    }

    public static Map<String, String> fields(JsonNode n) {
        var out = new LinkedHashMap<String, String>();
        if (n != null) n.properties().forEach(e -> out.put(e.getKey(), e.getValue().asText()));
        return out;
    }

    private LocalWire() {}
}
