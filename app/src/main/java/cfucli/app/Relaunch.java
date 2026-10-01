package cfucli.app;

import module java.base;

/** Starting another window as its own process, which is what the manager's "share" and "connect"
 *  entries do.
 *  <p>
 *  A separate process rather than another Stage, because a node holds its store exclusively and
 *  each window is a node. It also means the tray can be killed without taking any window with it.
 *  <p>
 *  Since 0.4 the window ships inside cfucli.exe, so when jr started this process the new window is
 *  that same exe with the window verb - its own name, icon and AOT cache - rather than a bare javaw. */
public final class Relaunch {

    public static Path jar() {
        try {
            var src = Relaunch.class.getProtectionDomain().getCodeSource();
            if (src == null) throw new IllegalStateException("cannot locate the cfucli app jar");
            return Paths.get(src.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the cfucli app jar", e);
        }
    }

    public static void spawn(String... args) {
        var command = new ArrayList<String>();
        var exe = selfExe();
        if (exe != null) {
            command.add(exe.toString());
            // While this run is the one creating the AOT cache, a second process would find no cache
            // either and try to write the same file; jr has no lock for that.
            if ("creating".equals(System.getenv("JR_AOT_STATE"))) command.add("-Xjr:aot=false");
            command.add("window");
        } else {
            // javaw rather than java: a window started from a tray menu must not drag a console along.
            var launcher = Paths.get(System.getProperty("java.home"), "bin", "javaw.exe");
            var java = Files.exists(launcher) ? launcher : Paths.get(System.getProperty("java.home"), "bin", "java");
            command.addAll(List.of(java.toString(), "-cp", jar().toString(), Main.class.getName()));
        }
        command.addAll(List.of(args));
        try {
            new ProcessBuilder(command).inheritIO().start();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start another cfucli window", e);
        }
    }

    /** The jr exe this process runs in, when it is one that carries the cli as well as the window
     *  (the cli's window verb is what it is started with). */
    static Path selfExe() {
        var self = System.getProperty("io.github.jarrunner.jr.exe");
        if (self == null || self.isBlank()) return null;
        try {
            Class.forName("cfucli.cli.CfuCli", false, Relaunch.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
        var p = Paths.get(self);
        return Files.isRegularFile(p) ? p : null;
    }

    private Relaunch() {}
}
