package cfucli.app;

import module java.base;
import jarrunner.updateutils.Places;

/** Installing from the window, for a person who downloaded cfucli.exe and double-clicked it.
 *  <p>
 *  Such an exe runs from wherever the browser put it, so "cfucli" is not on the PATH and there is
 *  no shortcut. The launcher says so and offers one key, which runs "cfucli install" in a console
 *  window of its own, left open until the person presses Enter, so they can read what happened. */
public final class SelfInstall {

    /** This exe, when it is a release exe that is not the installed copy; otherwise null. */
    public static Path candidate() {
        var exe = Relaunch.selfExe();
        if (exe == null || Places.os() != Places.Os.WINDOWS) return null;
        var installed = Places.installDir("cfucli").resolve("cfucli.exe");
        try {
            if (Files.exists(installed) && Files.isSameFile(installed, exe)) return null;
        } catch (IOException e) {
            return exe;
        }
        return exe;
    }

    public static void run(Path exe) {
        try {
            new ProcessBuilder("cmd.exe", "/c", "start", "\"cfucli install\"", exe.toString(), "install", "--pause").start();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start the installer", e);
        }
    }

    private SelfInstall() {}
}
