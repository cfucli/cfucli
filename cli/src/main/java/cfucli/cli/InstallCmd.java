package cfucli.cli;

import module java.base;
import cfucli.relay.Platform;
import picocli.CommandLine.*;

/** Installs the exe that is running, for someone who downloaded cfucli.exe and double-clicked it
 *  rather than running the one install line. It runs that same installer - so the folder, PATH,
 *  shortcuts and clean-up are exactly what the one line does - with CFUCLI_EXE naming this exe, so
 *  the installer copies it instead of downloading one. The window's F6 runs this. */
@Command(name = "install", description = "Install this cfucli.exe: copy it to the cfucli\\bin folder in your profile, put that on the PATH, add shortcuts.")
final class InstallCmd implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "usage for this verb")
    boolean help;

    @Override
    public Integer call() throws Exception {
        var exe = Window.selfExe();
        if (!Platform.isWindows() || exe.isEmpty()) {
            Out.err("cfucli: install copies a running cfucli.exe, and this is not one. Install with the one line instead:");
            Out.err(Platform.isWindows() ? "  irm " + UpdateCmd.SITE + "/install.ps1 | iex"
                                         : "  curl -fsSL " + UpdateCmd.SITE + "/install.sh | bash");
            return Exits.USAGE;
        }
        var cmd = List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                          "irm " + UpdateCmd.SITE + "/install.ps1 | iex");
        var pb = new ProcessBuilder(cmd).inheritIO();
        pb.environment().put("CFUCLI_EXE", exe.get().toAbsolutePath().toString());
        Out.line("Installing " + exe.get().toAbsolutePath());
        return pb.start().waitFor();
    }
}
