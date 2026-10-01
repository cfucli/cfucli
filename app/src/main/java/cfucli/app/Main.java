package cfucli.app;

/** JavaFX refuses to launch when main lives on the Application subclass inside a shaded jar - the
 *  module system cannot see javafx.graphics from there and reports the toolkit as missing. A plain
 *  holder class that calls launch is the standard way round it. */
public final class Main {

    public static void main(String[] args) {
        // Answered before JavaFX starts, so the installer can have cfucliapp.exe fetch its jar
        // without a window opening. The version is the one jr baked into the exe, when there is one.
        if (args.length == 1 && (args[0].equals("--version") || args[0].equals("-V"))) {
            System.out.println("cfucli window " + System.getProperty("io.github.jarrunner.jr.app.version", "(not from a release exe)"));
            return;
        }
        CfuCliApp.main(args);
    }

    private Main() {}
}
