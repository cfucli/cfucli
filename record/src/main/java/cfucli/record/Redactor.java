package cfucli.record;

import module java.base;

/** Keystrokes are recorded, and sooner or later someone types a password into a prompt. This
 *  catches the shapes that are recognisable - an assignment or flag whose name says secret, and
 *  long opaque blobs - and replaces the value.
 *  <p>
 *  It is a net, not a guarantee. A bare password typed at a prompt that asked for one in prose
 *  looks exactly like any other line, and nothing textual can tell them apart. The real controls
 *  for that are the recording toggle and {@code forget}, both of which exist for this reason. */
public final class Redactor {

    public static final String MASK = "[redacted]";

    static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)\\b(pass(word|wd)?|secret|token|api[_-]?key|auth|credential|bearer)\\b\\s*[:=]\\s*(\\S+)"),
            Pattern.compile("(?i)--(pass(word|wd)?|secret|token|api[_-]?key|auth)(=|\\s+)(\\S+)"),
            Pattern.compile("(?i)\\b(gh[pousr]_[A-Za-z0-9]{16,}|sk-[A-Za-z0-9]{16,}|xox[baprs]-[A-Za-z0-9-]{10,})"),
            Pattern.compile("rediss?://[^\\s@]*:([^\\s@]+)@"));

    public static String apply(String text) {
        if (text == null || text.isEmpty()) return text;
        var out = text;
        for (var p : PATTERNS) out = maskLastGroup(p, out);
        return out;
    }

    public static boolean wouldRedact(String text) {
        return text != null && !text.equals(apply(text));
    }

    static String maskLastGroup(Pattern p, String input) {
        var m = p.matcher(input);
        if (!m.find()) return input;
        m.reset();
        var out = new StringJoiner("");
        var last = 0;
        while (m.find()) {
            var g = m.groupCount();
            var start = m.start(g) < 0 ? m.start() : m.start(g);
            var end = m.end(g) < 0 ? m.end() : m.end(g);
            out.add(input.substring(last, start)).add(MASK);
            last = end;
        }
        out.add(input.substring(last));
        return out.toString();
    }

    private Redactor() {}
}
