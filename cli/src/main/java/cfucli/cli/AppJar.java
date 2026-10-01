package cfucli.cli;

import module java.base;
import cfucli.relay.SettingsStore;

/** Where the window lives, from the cli's point of view.
 *  <p>
 *  The cli has to be able to start a window, because a console an agent drives is worth much more
 *  when the human can watch it - and the node IS the window, so a headless node cannot grow one
 *  later. Since 0.4 the window is normally in this very jar, and then the thing to start is this
 *  cfucli itself with the window verb: the exe when jr started us, or this jar otherwise. Only a
 *  cli-only jar (a build of the cli module alone) has to look further - named in settings, or
 *  across the reactor the way a built one sits. */
public final class AppJar {

    public static final String NAME = "cfucli-app.jar", MAIN = Window.MAIN;

    public static Optional<Path> find() {
        if (Window.available()) return Optional.of(Window.selfExe().orElseGet(Self::jar));
        var configured = SettingsStore.load().appJar();
        if (configured != null && !configured.isBlank()) {
            var p = Paths.get(configured.trim());
            return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
        }
        var here = Self.jar().toAbsolutePath();
        var dir = here.getParent();
        return candidates(dir).filter(Files::isRegularFile).findFirst();
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
        return "this jar has no window in it; looked next to " + Self.jar().toAbsolutePath().getParent()
               + " and across the build for " + NAME + "; set appJar in " + SettingsStore.path() + " to name it";
    }

    private AppJar() {}
}
