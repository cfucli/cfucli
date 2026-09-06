package cfucli.record;

import module java.base;

/** Stream names, as stored. Strings rather than an enum because the DataHelper processor does not
 *  accept enum fields, and because a name that outlives a code change is worth more here than a
 *  compile-time check. */
public final class Streams {

    public static final String
            INPUT = "input",
            OUTPUT = "output",
            EXEC_STDOUT = "exec_stdout",
            EXEC_STDERR = "exec_stderr",
            CONTROL = "control",
            ERROR = "error";

    public static final List<String> ALL = List.of(INPUT, OUTPUT, EXEC_STDOUT, EXEC_STDERR, CONTROL, ERROR);
    public static final List<String> OUTPUT_LIKE = List.of(OUTPUT, EXEC_STDOUT);
    public static final List<String> ERROR_LIKE = List.of(EXEC_STDERR, ERROR);

    /** What the shell and its commands produced, with none of the tool's own voice mixed in.
     *  The default for reading a transcript back, for the same reason the window keeps the two
     *  apart: a log line spliced into a program's output belongs to neither and corrupts both. */
    public static final List<String> SHELL = List.of(OUTPUT, EXEC_STDOUT, EXEC_STDERR);

    /** Control and error records are the tool's own voice, and each is one whole message rather
     *  than a slice of a byte stream. Everything that renders a transcript as lines depends on
     *  that, which is why the terminator is applied where the record is written and not by each
     *  of the fourteen places that write one. */
    public static boolean isNote(String stream) {
        return CONTROL.equals(stream) || ERROR.equals(stream);
    }

    public static List<String> resolve(String spec) {
        if (spec == null || spec.isBlank() || "all".equalsIgnoreCase(spec)) return ALL;
        var out = new LinkedHashSet<String>();
        for (var part : spec.split("[,+]")) {
            var p = part.trim().toLowerCase();
            switch (p) {
                case "out", "stdout" -> out.addAll(OUTPUT_LIKE);
                case "err", "stderr" -> out.addAll(ERROR_LIKE);
                case "in", "stdin" -> out.add(INPUT);
                case "shell" -> out.addAll(SHELL);
                default -> {
                    if (!ALL.contains(p)) throw new IllegalArgumentException("unknown stream '" + part + "', try one of " + ALL);
                    out.add(p);
                }
            }
        }
        return List.copyOf(out);
    }

    private Streams() {}
}
