package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

/** The window, as a verb. {@code cfucli} with no arguments is the same as {@code cfucli window}:
 *  the launcher, which is what a double-click or the desktop shortcut opens. The flags pick a role
 *  directly, for the tray, for scripts and for the cli itself when it starts a console. */
@Command(name = "window", description = "Open the window: the launcher, or straight into a role with --host, --join or --tray.")
public final class WindowCmd implements Callable<Integer> {

    @ArgGroup(exclusive = true)
    Role role;

    static final class Role {
        @Option(names = "--host", description = "share this machine's shell, in a window")
        boolean host;

        @Option(names = "--join", description = "connect to a shared session (with its id, and -p)")
        boolean join;

        @Option(names = "--tray", description = "the tray icon, which manages whatever runs on this machine")
        boolean tray;
    }

    @Parameters(index = "0", arity = "0..1", description = "with --join: the session id")
    String sessionId;

    @Option(names = {"-p", "--password"}, description = "with --join: the one-time password")
    String password;

    @Option(names = "--node", description = "the node this window is (default: default, or a free name if that is taken)")
    String node;

    @Option(names = "--shell", description = "with --host: the shell to run (default: from settings)")
    String shell;

    @Option(names = "--local", description = "with --host: share on this machine only - nothing is announced on the relay")
    boolean local;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "usage for this verb; see 'cfucli guide' for the manual")
    boolean help;

    @Override
    public Integer call() {
        if (!Window.available()) {
            Out.err("cfucli: this cfucli was built without the window. Install a release, or run the window jar itself.");
            return Exits.USAGE;
        }
        Window.run(args());
        return 0;
    }

    /** The command line the window itself parses (cfucli.app.AppArgs). */
    List<String> args() {
        var a = new ArrayList<String>();
        if (role != null && role.host) a.add("--host");
        if (role != null && role.tray) a.add("--tray");
        if (role != null && role.join) {
            a.add("--join");
            if (sessionId != null) a.add(sessionId);
        }
        if (password != null) a.addAll(List.of("--password", password));
        if (node != null) a.addAll(List.of("--node", node));
        if (shell != null) a.addAll(List.of("--shell", shell));
        if (local) a.add("--local");
        return a;
    }
}
