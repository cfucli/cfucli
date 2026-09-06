package cfucli.app;

/** JavaFX refuses to launch when main lives on the Application subclass inside a shaded jar - the
 *  module system cannot see javafx.graphics from there and reports the toolkit as missing. A plain
 *  holder class that calls launch is the standard way round it. */
public final class Main {

    public static void main(String[] args) {
        CfuCliApp.main(args);
    }

    private Main() {}
}
