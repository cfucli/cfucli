package cfucli.relay;

/** The handful of things that differ by OS: which shell a new session opens, and what the java
 *  launcher next to this jar is called. Everything else in this project - pty4j, the relay, the
 *  recorder - is already cross-platform; this is where the Windows-only assumptions used to hide. */
public final class Platform {

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** cmd.exe on Windows; the user's own shell (falling back to a sane default) elsewhere. */
    public static String defaultShell() {
        if (isWindows()) return "cmd.exe";
        var sh = System.getenv("SHELL");
        return sh == null || sh.isBlank() ? "/bin/zsh" : sh;
    }

    /** The java launcher next to whatever JVM is running this: java(w).exe on Windows, java
     *  everywhere else - pty4j and ProcessBuilder both take a bare name and resolve it fine. */
    public static String javaExecutable(boolean windowless) {
        return isWindows() ? (windowless ? "javaw.exe" : "java.exe") : "java";
    }

    private Platform() {}
}
