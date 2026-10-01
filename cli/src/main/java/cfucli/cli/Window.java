package cfucli.cli;

import module java.base;

/** The window, when it is in the same jar as this cli.
 *  <p>
 *  Since 0.4 a release is one jar and one exe: the window's classes ship beside the cli's, and
 *  {@code cfucli} with no arguments, or {@code cfucli window ...}, opens it. The cli module does not
 *  depend on the window module - the window needs JavaFX, which is per platform, and the cli must
 *  stay buildable and runnable without it - so the window is reached by name, and a cli-only jar
 *  simply reports that it has none. */
public final class Window {

    public static final String MAIN = "cfucli.app.Main";

    public static boolean available() {
        try {
            Class.forName(MAIN, false, Window.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Runs the window in this process; returns when it has closed. */
    public static void run(List<String> args) {
        try {
            var main = Class.forName(MAIN, true, Window.class.getClassLoader()).getMethod("main", String[].class);
            main.invoke(null, (Object) args.toArray(String[]::new));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            if (e.getCause() instanceof Error err) throw err;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("this cfucli has no window in it (" + e + ")", e);
        }
    }

    /** The exe jr started this process from, when it was a jr exe. */
    public static Optional<Path> selfExe() {
        var self = System.getProperty("io.github.jarrunner.jr.exe");
        if (self == null || self.isBlank()) return Optional.empty();
        var p = Paths.get(self);
        return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    /** The leading jr options for an exe this process starts. While this run is creating the AOT
     *  cache (the first run of a new jar), a second process started now would find no cache either
     *  and try to create the same file - jr has no lock for that - so it runs without one. */
    public static List<String> jrArgs() {
        return "creating".equals(System.getenv("JR_AOT_STATE")) ? List.of("-Xjr:aot=false") : List.of();
    }

    private Window() {}
}
