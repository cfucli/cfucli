package cfucli.app;

import module java.base;

/** Starting another window as its own process, which is what the manager's "share" and "connect"
 *  entries do.
 *  <p>
 *  A separate process rather than another Stage, because a node holds its store exclusively and
 *  each window is a node. It also means the tray can be killed without taking any window with it. */
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
        // javaw rather than java: a window started from a tray menu must not drag a console along.
        var launcher = Paths.get(System.getProperty("java.home"), "bin", "javaw.exe");
        var java = Files.exists(launcher) ? launcher : Paths.get(System.getProperty("java.home"), "bin", "java");
        var command = new ArrayList<String>(List.of(java.toString(), "-cp", jar().toString(),
                Main.class.getName()));
        command.addAll(List.of(args));
        try {
            new ProcessBuilder(command).inheritIO().start();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start another cfucli window", e);
        }
    }

    private Relaunch() {}
}
