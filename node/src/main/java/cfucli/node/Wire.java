package cfucli.node;

import module java.base;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** One JSON object per line, both ways. Small enough to read in a terminal when something is
 *  wrong, which is most of why it is not a binary protocol. */
public final class Wire {

    public static final ObjectMapper JSON = new ObjectMapper();

    public static ObjectNode obj() {
        return JSON.createObjectNode();
    }

    public static ObjectNode request(String verb, Map<String, Object> args) {
        var n = obj();
        n.put("verb", verb);
        n.set("args", JSON.valueToTree(args == null ? Map.of() : args));
        return n;
    }

    public static ObjectNode ok(JsonNode result) {
        var n = obj();
        n.put("ok", true);
        n.set("result", result == null ? JSON.nullNode() : result);
        return n;
    }

    public static ObjectNode fail(String message) {
        var n = obj();
        n.put("ok", false);
        n.put("error", message == null ? "unknown error" : message);
        return n;
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

    public static String str(JsonNode args, String key, String fallback) {
        var v = args == null ? null : args.get(key);
        return v == null || v.isNull() ? fallback : v.asText();
    }

    public static int i(JsonNode args, String key, int fallback) {
        var v = args == null ? null : args.get(key);
        return v == null || v.isNull() ? fallback : v.asInt();
    }

    public static long l(JsonNode args, String key, long fallback) {
        var v = args == null ? null : args.get(key);
        return v == null || v.isNull() ? fallback : v.asLong();
    }

    public static Long boxedLong(JsonNode args, String key) {
        var v = args == null ? null : args.get(key);
        return v == null || v.isNull() ? null : v.asLong();
    }

    public static Integer boxedInt(JsonNode args, String key) {
        var v = args == null ? null : args.get(key);
        return v == null || v.isNull() ? null : v.asInt();
    }

    public static boolean bool(JsonNode args, String key, boolean fallback) {
        var v = args == null ? null : args.get(key);
        return v == null || v.isNull() ? fallback : v.asBoolean();
    }

    private Wire() {}
}
