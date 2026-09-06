package cfucli.cli;

import module java.base;

/** How a script file is turned into a command line for the far machine.
 *  <p>
 *  Kept apart from {@link RunScriptCmd} so the mapping is one readable table rather than a switch
 *  buried in a picocli class, and so it can be exercised without a node to talk to. */
public final class Interpreters {

    public static final String
            POWERSHELL = "powershell",
            CMD = "cmd",
            BASH = "bash";

    /** Null when it cannot be told, which the caller reports rather than guessing at: guessing
     *  wrong here means handing someone else's machine a file and an interpreter that will make
     *  nonsense of it. */
    public static String of(String requested, String fileName) {
        if (requested != null && !"auto".equalsIgnoreCase(requested)) {
            return switch (requested.toLowerCase(Locale.ROOT)) {
                case POWERSHELL, "pwsh", "ps", "ps1" -> POWERSHELL;
                case CMD, "bat", "batch" -> CMD;
                case BASH, "sh" -> BASH;
                default -> null;
            };
        }
        var lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ps1")) return POWERSHELL;
        if (lower.endsWith(".cmd") || lower.endsWith(".bat")) return CMD;
        if (lower.endsWith(".sh")) return BASH;
        return null;
    }

    /** -NoProfile so the far machine's own profile cannot change what the script sees, and
     *  -ExecutionPolicy Bypass because a staged file is unsigned and the default policy refuses it
     *  outright - which reads as the script failing rather than as never having run. */
    public static String command(String kind, String remotePath, List<String> args) {
        var tail = args == null || args.isEmpty() ? ""
                : " " + args.stream().map(Interpreters::arg).collect(Collectors.joining(" "));
        return switch (kind) {
            case POWERSHELL -> "powershell -NoProfile -ExecutionPolicy Bypass -File " + quote(remotePath) + tail;
            case CMD -> "cmd /c " + quote(remotePath) + tail;
            case BASH -> "bash " + quote(remotePath) + tail;
            default -> throw new IllegalArgumentException("no interpreter for " + kind);
        };
    }

    public static String delete(String kind, String remotePath) {
        return BASH.equals(kind) ? "rm -f " + quote(remotePath) : "cmd /c del /q " + quote(remotePath);
    }

    /** A staging name of its own rather than the file's, so a run cannot overwrite something the
     *  far machine already had under that name, and two runs cannot collide with each other.
     *  <p>
     *  The extension comes from the INTERPRETER, not from the local file: PowerShell's -File
     *  refuses anything not named .ps1, and it does not fail loudly - it prints its banner and
     *  looks for all the world like a script that ran and produced nothing. Measured with
     *  --shell powershell against an extensionless file, which is exactly when the override is
     *  reached for. */
    public static String stagedName(String kind, String fileName) {
        var dot = fileName.lastIndexOf('.');
        var stem = dot < 0 ? fileName : fileName.substring(0, dot);
        return "cfucli-run-" + safe(stem) + "-"
                + Long.toString(Math.abs(new SecureRandom().nextLong()), 36) + extensionFor(kind);
    }

    /** Bash needs none, but one costs nothing and says what the file is. */
    public static String extensionFor(String kind) {
        return switch (kind) {
            case POWERSHELL -> ".ps1";
            case CMD -> ".cmd";
            case BASH -> ".sh";
            default -> "";
        };
    }

    /** Only PowerShell cares, and only on the path where nothing is staged - a local file cannot
     *  be renamed on the caller's behalf, so this is a refusal rather than a fix. */
    public static boolean nameIsUsable(String kind, String fileName) {
        return !POWERSHELL.equals(kind) || fileName.toLowerCase(Locale.ROOT).endsWith(".ps1");
    }

    static String safe(String s) {
        var out = s.replaceAll("[^A-Za-z0-9._-]", "");
        return out.isBlank() ? "script" : out.length() > 32 ? out.substring(0, 32) : out;
    }

    /** The command reaches the far shell as one string, so an argument with a space in it arrives
     *  as two unless it is quoted here - which is the same class of bug this whole verb exists to
     *  avoid, one layer further in. Measured: "second value" arrived as "second". */
    static String arg(String a) {
        if (a == null || a.isEmpty()) return "\"\"";
        if (a.startsWith("\"") || a.chars().noneMatch(Character::isWhitespace)) return a;
        return "\"" + a.replace("\"", "\\\"") + "\"";
    }

    static String quote(String path) {
        return path.startsWith("\"") ? path : "\"" + path + "\"";
    }

    private Interpreters() {}
}
