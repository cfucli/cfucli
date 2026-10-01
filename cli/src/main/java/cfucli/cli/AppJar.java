package cfucli.cli;

import module java.base;
import cfucli.relay.SettingsStore;

/** Where the window lives, from the cli's point of view.
 *  <p>
 *  The cli has to be able to start a window, because a console an agent drives is worth much more
 *  when the human can watch it - and the node IS the window, so a headless node cannot grow one
 *  later. Looked for in the three places it can honestly be: named in settings, next to this jar
 *  the way an installed pair sits, or across the reactor the way a built one does. */
public final class AppJar {

    public static final String NAME = "cfucli-app.jar", MAIN = "cfucli.app.Main", EXE = "cfucliapp.exe";

    public static Optional<Path> find() {
        var configured = SettingsStore.load().appJar();
        if (configured != null && !configured.isBlank()) {
            var p = Paths.get(configured.trim());
            return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
        }
        var exe = siblingExe();
        if (exe.isPresent()) return exe;
        var here = Self.jar().toAbsolutePath();
        var dir = here.getParent();
        return candidates(dir).filter(Files::isRegularFile).findFirst();
    }

    /** cfucliapp.exe beside the running cfucli.exe. Since 0.4 each exe keeps its own jar in jr's
     *  cache, a folder per jar hash, so the window's jar is never next to this one; the exe is the
     *  thing to start, and it fetches its jar itself. jr names its exe in this property. */
    static Optional<Path> siblingExe() {
        var self = System.getProperty("io.github.jarrunner.jr.exe");
        if (self == null || self.isBlank()) return Optional.empty();
        var dir = Paths.get(self).getParent();
        var app = dir == null ? null : dir.resolve(EXE);
        return app != null && Files.isRegularFile(app) ? Optional.of(app) : Optional.empty();
    }

    /** True when the window is started as an exe rather than as a jar on a java command line. */
    public static boolean isExe(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe");
    }

    static Stream<Path> candidates(Path cliJarDir) {
        if (cliJarDir == null) return Stream.of();
        var reactor = cliJarDir.getParent() == null ? null : cliJarDir.getParent().getParent();
        return Stream.of(
                cliJarDir.resolve(NAME),
                reactor == null ? cliJarDir.resolve(NAME) : reactor.resolve("app").resolve("shade").resolve(NAME),
                reactor == null ? cliJarDir.resolve(NAME) : reactor.resolve("app").resolve("target").resolve(NAME));
    }

    /** Says where it looked, because "cannot find the window" is useless without that. */
    public static String whereItLooked() {
        var configured = SettingsStore.load().appJar();
        if (configured != null && !configured.isBlank()) return "appJar in " + SettingsStore.path() + " points at " + configured;
        return "looked next to " + Self.jar().toAbsolutePath().getParent() + " and across the build for " + NAME
               + "; set appJar in " + SettingsStore.path() + " to name it";
    }

    private AppJar() {}
}
