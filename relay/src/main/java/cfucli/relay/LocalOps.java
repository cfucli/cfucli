package cfucli.relay;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** The whole local wire protocol in one switch: the seven things {@link RelayTransport} can ask
 *  for. Separate from the socket so the shape of a request is one readable thing rather than
 *  something you have to reconstruct from a client and a server that must agree. */
public final class LocalOps {

    public static ObjectNode request(String op, String token) {
        return LocalWire.obj().put("op", op).put("token", token);
    }

    public static ObjectNode apply(LocalStore store, JsonNode req) {
        var op = LocalWire.str(req, "op", "");
        var res = LocalWire.obj().put("ok", true);
        switch (op) {
            case "append" -> res.put("id", store.append(LocalWire.str(req, "stream", ""),
                    LocalWire.unb64(LocalWire.str(req, "payload", "")), LocalWire.num(req, "maxLen", 0)));
            case "read" -> {
                var records = res.putArray("records");
                for (var r : store.read(LocalWire.str(req, "stream", ""), LocalWire.str(req, "from", RelayTransport.FIRST_ID),
                        (int) LocalWire.num(req, "count", 256), Duration.ofMillis(LocalWire.num(req, "blockMs", 0)))) {
                    records.addObject().put("id", r.id()).put("payload", LocalWire.b64(r.payload()));
                }
            }
            case "lastId" -> res.put("id", store.lastId(LocalWire.str(req, "stream", "")));
            case "putMeta" -> {
                store.putMeta(LocalWire.str(req, "key", ""), LocalWire.fields(req.get("fields")), null);
                res.put("stored", true);
            }
            case "getMeta" -> {
                var fields = res.putObject("fields");
                store.getMeta(LocalWire.str(req, "key", "")).forEach(fields::put);
            }
            case "delete" -> {
                var keys = new ArrayList<String>();
                var node = req.get("keys");
                if (node != null) node.forEach(k -> keys.add(k.asText()));
                store.delete(keys.toArray(String[]::new));
                res.put("deleted", keys.size());
            }
            case "ping" -> res.put("pong", true);
            default -> throw new IllegalArgumentException("unknown local op '" + op + "'");
        }
        return res;
    }

    private LocalOps() {}
}
