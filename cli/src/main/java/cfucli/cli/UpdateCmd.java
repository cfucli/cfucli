package cfucli.cli;

import module java.base;
import cfucli.relay.Platform;
import jarrunner.updateutils.Jr;
import jarrunner.updateutils.UpdateResult;
import jarrunner.updateutils.Updater;
import picocli.CommandLine.*;

/** Replaces this cfucli exe with the newest release, through jr (-Xjr:update): jr reads the update file named in the
 *  config it carries, checks the download's sha256 and swaps the exe, even while it runs. What cfucli adds around it:
 *  running nodes are stopped first, so the next command starts on the new code, and the new exe is run once so it
 *  fetches its jar now rather than on first use. */
@Command(name = "update", description = "Update cfucli to the newest release. The new version runs from the next command.")
final class UpdateCmd implements Callable<Integer> {

    public static final String SITE = "https://cfucli.github.io";

    @Option(names = "--check", description = "only say whether a newer release is out")
    boolean checkOnly;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "usage for this verb")
    boolean help;

    @Override
    public Integer call() throws Exception {
        var exe = Jr.exe();
        if (exe.isEmpty()) {
            Out.err("cfucli: update replaces the cfucli exe, and this cfucli was not started from one.");
            Out.err("Download the newest from " + SITE);
            return Exits.USAGE;
        }
        var check = Updater.run(false, true);
        Out.line(check.message());
        if (check.status() != UpdateResult.Status.NEWER || checkOnly) return check.ok() ? Exits.OK : Exits.FAILED;

        for (var node : RunningNodes.stopAll()) Out.line("stopped node '" + node + "' so it restarts on the new version");
        var r = Updater.update(); // jr shows its progress and its result on stderr
        if (!r.updated()) return r.ok() ? Exits.OK : Exits.FAILED;

        Out.line("fetching the new cfucli jar (about 90 MB) so the next command starts at once");
        var warm = new ProcessBuilder(exe.get().toString(), "-Xjr:yes", "-Xjr:aot=false", "-V")
                .redirectInput(ProcessBuilder.Redirect.from(new File(Platform.isWindows() ? "NUL" : "/dev/null")))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (warm.waitFor() != 0) Out.line("the new version will fetch its jar on its first run instead");
        Out.line("updated to " + r.latest());
        return Exits.OK;
    }
}
