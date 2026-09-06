package cfucli.cli;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Printing. Human output goes to stdout in a shape a person can read; --json prints the node's
 *  answer unchanged, which is what a script or an agent should parse. Errors go to stderr as one
 *  line, never a stack trace. */
public final class Out {

    static final ObjectMapper JSON = new ObjectMapper();

    public static void json(JsonNode n) {
        try {
            System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(n));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void line(String s) {
        System.out.println(s);
    }

    public static void err(String s) {
        System.err.println(s);
    }

    public static String time(long epochMillis) {
        return epochMillis <= 0 ? "-" : DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis));
    }

    public static String bytes(long n) {
        if (n < 1024) return n + "B";
        if (n < 1024 * 1024) return Math.round(n / 1024.0) + "K";
        return String.format(Locale.ROOT, "%.1fM", n / (1024.0 * 1024));
    }

    public static String millis(long ms) {
        return ms < 1000 ? ms + "ms" : String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
    }

    public static String pad(String s, int width) {
        var t = s == null ? "" : s;
        return t.length() >= width ? t : t + " ".repeat(width - t.length());
    }

    public static String oneLine(String s, int max) {
        if (s == null) return "";
        var t = s.replace("\r", "").replace("\n", "¶ ").replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private Out() {}
}
