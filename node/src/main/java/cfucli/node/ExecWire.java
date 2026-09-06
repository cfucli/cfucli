package cfucli.node;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;

/** The JSON carried inside exec frames. Kept separate from the frame types so the relay module
 *  stays ignorant of what a job is. */
public final class ExecWire {

    public static byte[] request(String jobId, String command, String cwd, long timeoutMs, String stdin) {
        var n = Wire.obj();
        n.put("jobId", jobId);
        n.put("command", command);
        if (cwd != null) n.put("cwd", cwd);
        n.put("timeoutMs", timeoutMs);
        if (stdin != null && !stdin.isEmpty()) n.put("stdin", stdin);
        return bytes(n);
    }

    public static byte[] chunk(String jobId, String text) {
        var n = Wire.obj();
        n.put("jobId", jobId);
        n.put("text", text);
        return bytes(n);
    }

    public static byte[] exit(String jobId, Integer exitCode, String state, long outBytes, long errBytes, long millis) {
        var n = Wire.obj();
        n.put("jobId", jobId);
        if (exitCode == null) n.putNull("exitCode"); else n.put("exitCode", exitCode);
        n.put("state", state);
        n.put("stdoutBytes", outBytes);
        n.put("stderrBytes", errBytes);
        n.put("millis", millis);
        return bytes(n);
    }

    public static byte[] cancel(String jobId) {
        var n = Wire.obj();
        n.put("jobId", jobId);
        return bytes(n);
    }

    public static JsonNode read(byte[] payload) {
        return Wire.parse(new String(payload, StandardCharsets.UTF_8));
    }

    static byte[] bytes(JsonNode n) {
        return Wire.line(n).getBytes(StandardCharsets.UTF_8);
    }

    public static String newJobId() {
        return "j" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(COUNTER.incrementAndGet(), 36);
    }

    static final AtomicInteger COUNTER = new AtomicInteger();

    private ExecWire() {}
}
