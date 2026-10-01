package cfucli.app;

import module java.base;

/** Installing from the window, for a person who downloaded cfucli.exe and double-clicked it.
 *  <p>
 *  Such an exe runs from wherever the browser put it, so "cfucli" is not on the PATH and there is
 *  no shortcut. The launcher says so and offers one key, which runs "cfucli install" in a console
 *  window the person can watch: the same installer as the one-line install, using this exe instead
 *  of downloading one. */
public final class SelfInstall {

    /** This exe, when it is a release exe that is not the installed copy; otherwise null. */
    public static Path candidate() {
        var exe = Relaunch.selfExe();
        if (exe == null || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return null;
        var installed = Paths.get(System.getProperty("user.home"), "cfucli", "bin", "cfucli.exe");
        try {
            if (Files.exists(installed) && Files.isSameFile(installed, exe)) return null;
        } catch (IOException e) {
            return exe;
        }
        return exe;
    }

    /** A console window of its own, left open so the person can read what happened. */
    public static void run(Path exe) {
        var command = "& '" + exe.toString().replace("'", "''") + "' install";
        try {
            new ProcessBuilder("cmd.exe", "/c", "start", "\"cfucli install\"", "powershell.exe",
                    "-NoProfile", "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", command).start();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start the installer", e);
        }
    }

    private SelfInstall() {}
}
