package cfucli.node;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;

/** The JSON carried inside the four FILE frames, and the field names both ends agree on.
 *  <p>
 *  Kept beside {@link ExecWire} for the same reason: the relay module knows a frame has a type and
 *  a payload, and nothing about what a transfer is. */
public final class FileWire {

    public static final String
            XFER = "xferId", NAME = "name", DEST = "dest", SIZE = "size", SHA = "sha256",
            ROUTE = "route", CHUNKS = "chunks", CHUNK_BYTES = "chunkBytes", INDEX = "index", DATA = "data",
            RELATIVE = "relative", SOURCE = "sourcePath", FORCE = "force", WAIT_MS = "waitMs",
            OK = "ok", ERROR = "error", PATH = "path", BYTES = "bytes", MILLIS = "millis",
            SHARED = "sharedAvailable", SAME_MACHINE = "sameMachine", VIA = "via", WHY = "why";

    /** Big enough that a few hundred kilobytes is a handful of relay messages, small enough that
     *  one message stays far below any request-size limit Upstash imposes once the payload has been
     *  encrypted and base64'd on the way out. */
    public static final int CHUNK_BYTES_DEFAULT = 64 * 1024;

    public static byte[] bytes(JsonNode n) {
        return Wire.line(n).getBytes(StandardCharsets.UTF_8);
    }

    public static JsonNode read(byte[] payload) {
        return Wire.parse(new String(payload, StandardCharsets.UTF_8));
    }

    public static String newXferId() {
        return "x" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(COUNTER.incrementAndGet(), 36);
    }

    static final AtomicInteger COUNTER = new AtomicInteger();

    private FileWire() {}
}
