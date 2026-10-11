package cfucli.cli;

import module java.base;
import cfucli.relay.Home;
import cfucli.relay.Platform;
import jarrunner.jr.apputils.Install;
import jarrunner.jr.apputils.JrApp;
import jarrunner.jr.apputils.Places;
import picocli.CommandLine.*;

/** Installs the cfucli that is running, wherever it was downloaded to: the one-file install. On Windows the exe goes
 *  to %LOCALAPPDATA%\Programs\cfucli, first on the user PATH, with Start Menu and Desktop shortcuts; on macOS jr
 *  writes ~/Applications/cfucli.app and links ~/.local/bin/cfucli to it (jarrunner jr-apputils, Install). Running it
 *  again repairs the install. The window's F6 runs this. Settings and node state stay in ~/cfucli, untouched.
 *  <p>
 *  It also takes away what installs before 0.5 left: %USERPROFILE%\cfucli\bin on the PATH and the exe in it, the old
 *  Start Menu folder, and the versions/runtime folders, so there is one cfucli on the machine, not two. */
@Command(name = "install", description = "Install this cfucli: copy it to the standard place for programs, put it on the PATH, add shortcuts.")
final class InstallCmd implements Callable<Integer> {

    @Option(names = "--uninstall", description = "remove what install made (settings in ~/cfucli are kept)")
    boolean uninstall;

    @Option(names = "--pause", description = "wait for Enter at the end (the window runs install in a console of its own)")
    boolean pause;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "usage for this verb")
    boolean help;

    @Override
    public Integer call() throws Exception {
        var code = run();
        if (pause) {
            Out.line("");
            Out.line("Press Enter to close this window.");
            try {
                System.in.read();
            } catch (IOException ignored) {
            }
        }
        return code;
    }

    private int run() throws Exception {
        if (uninstall) {
            RunningNodes.stopAll();
            return Install.uninstall("cfucli", Out::line).ok() ? Exits.OK : Exits.FAILED;
        }
        if (JrApp.exe().isEmpty()) {
            Out.err("cfucli: install copies the running cfucli exe, and this cfucli was not started from one.");
            Out.err("Download it from " + UpdateCmd.SITE + " and run it with: install");
            return Exits.USAGE;
        }
        for (var node : RunningNodes.stopAll()) Out.line("stopped node '" + node + "' so it restarts on this version");
        if (Places.os() == Places.Os.MACOS) removeOldMacApp();
        var r = Install.app("cfucli").displayName("cfucli").description("cfucli - share a shell, or join one")
                .desktopShortcut(true).workingDir(Home.dir()).log(Out::line).run();
        if (Platform.isWindows()) removeOldWindowsLayout();
        removeOldFolders();
        if (!r.ok()) return Exits.FAILED;
        Out.line("");
        Out.line("  cfucli is installed: " + r.command());
        Out.line("  First time on this machine? Two things, once:");
        Out.line("    cfucli relay set --from <the relay file you were sent>");
        Out.line("    cfucli available on --as <your name>");
        Out.line("  To update later:  cfucli update");
        return Exits.OK;
    }

    /** 0.4 and earlier on Windows: %USERPROFILE%\cfucli\bin, first on the user PATH, and a Start Menu folder. */
    private static void removeOldWindowsLayout() {
        var bin = Home.dir().resolve("bin");
        if (Install.removeFromUserPath(bin)) Out.line("took the old " + bin + " off your PATH");
        try {
            var oldMenu = Places.menuDir().resolve("cfucli");
            Files.deleteIfExists(oldMenu.resolve("cfucli.lnk"));
            Files.deleteIfExists(oldMenu);
        } catch (IOException ignored) {
        }
        if (!Files.isDirectory(bin)) return;
        try (var files = Files.list(bin)) {
            for (var f : files.toList()) {
                var name = f.getFileName().toString();
                if (!(name.startsWith("cfucli") && (name.endsWith(".exe") || name.endsWith(".old") || name.endsWith(".jrc")
                        || name.endsWith(".cmd") || name.endsWith(".jr-replaced")))) continue;
                try {
                    Files.delete(f);
                } catch (IOException running) { // the old exe may be what is running this command
                    var aside = f.resolveSibling(name + ".jr-replaced");
                    try {
                        Files.move(f, aside, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(bin);
        } catch (IOException ignored) { // a renamed running exe is still in it; the next install removes it
        }
    }

    /** 0.4 and earlier on macOS: install.sh's own cfucli.app, which ran a launcher script, and that launcher. */
    private static void removeOldMacApp() {
        var app = Places.home().resolve("Applications/cfucli.app");
        if (isScript(app.resolve("Contents/MacOS/cfucli"))) deleteTree(app); // jr writes a Mach-O binary there; install.sh wrote a script
        try {
            Files.deleteIfExists(Home.dir().resolve("bin/cfucli"));
            Files.deleteIfExists(Home.dir().resolve("bin/cfucliapp"));
            Files.deleteIfExists(Home.dir().resolve("bin"));
        } catch (IOException ignored) {
        }
    }

    private static boolean isScript(Path f) {
        try (var in = Files.newInputStream(f)) {
            return in.read() == 0x23 && in.read() == 0x21;
        } catch (IOException e) {
            return false;
        }
    }

    /** Per-version jar folders, a private runtime and old shortcut files: the exe keeps its own jar and Java now. */
    private static void removeOldFolders() {
        for (var name : List.of("versions", "runtime", "shortcuts")) {
            var dir = Home.dir().resolve(name);
            if (!Files.isDirectory(dir)) continue;
            var aside = Home.dir().resolve(name + ".removing-" + Long.toHexString(System.nanoTime()));
            try {
                Files.move(dir, aside); // fails if anything inside is open, so a running old version is never cut off
            } catch (IOException inUse) {
                continue;
            }
            deleteTree(aside);
        }
        try {
            Files.deleteIfExists(Home.dir().resolve("cfucli.ico"));
        } catch (IOException ignored) {
        }
    }

    private static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            for (var p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }
}
