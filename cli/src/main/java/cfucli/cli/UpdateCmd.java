package cfucli.cli;

import module java.base;
import cfucli.relay.Platform;
import picocli.CommandLine.*;

/** Runs the same one line a person installs with, because install and update are the same act:
 *  fetch the latest release, put it in its own versions folder, point the launchers at it.
 *  <p>
 *  Its own folder per version is what makes this safe to run from inside the tool being replaced.
 *  A running JVM holds its jar open, and on Windows an open file cannot be overwritten - so nothing
 *  is ever overwritten; the launchers are repointed and the old folder is left for the next run to
 *  sweep. Running nodes are stopped by the installer, so the next command starts on new code
 *  rather than a node still holding the old classes. */
@Command(name = "update", description = "Install the latest cfucli release - the same one line used to install it.")
final class UpdateCmd implements Callable<Integer> {

    public static final String SITE = "https://cfucli.github.io";

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "usage for this verb")
    boolean help;

    @Override
    public Integer call() throws Exception {
        var cmd = Platform.isWindows()
                ? List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                          "irm " + SITE + "/install.ps1 | iex")
                : List.of("bash", "-c", "curl -fsSL " + SITE + "/install.sh | bash");
        var pb = new ProcessBuilder(cmd).inheritIO();
        // Update the copy that is running, wherever it was installed: an installed jar sits at
        // <home>/versions/<version>/cfucli.jar. Anything else - a jar built from source, or a
        // launcher someone keeps in their own tools folder - is not the installer's to replace,
        // so it gets a fresh install in the default place and is told so.
        var home = exeHome();
        if (home == null) home = installHome(Self.jar().toAbsolutePath());
        if (home != null) {
            pb.environment().put("CFUCLI_HOME", home.toString());
            Out.line("Updating the install in " + home);
        } else {
            Out.line("This cfucli runs from " + Self.jar().toAbsolutePath() + ", which the installer did not put there -"
                     + " installing the latest release into the default folder instead. This copy is left as it is.");
        }
        Out.line("Running: " + cmd.getLast());
        return pb.start().waitFor();
    }

    /** Since 0.4 the installer puts only cfucli.exe in <home>/bin, and its jar lives in jr's cache;
     *  jr names the running exe in this property. */
    static Path exeHome() {
        var exe = System.getProperty("io.github.jarrunner.jr.exe");
        if (exe == null || exe.isBlank()) return null;
        var bin = Paths.get(exe).toAbsolutePath().getParent();
        if (bin == null || !"bin".equals(String.valueOf(bin.getFileName()))) return null;
        return bin.getParent();
    }

    static Path installHome(Path jar) {
        var versionDir = jar.getParent();
        var versions = versionDir == null ? null : versionDir.getParent();
        if (versions == null || !"versions".equals(String.valueOf(versions.getFileName()))) return null;
        return versions.getParent();
    }
}
