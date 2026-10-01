package cfucli.cli;

import module java.base;
import cfucli.node.NodeClient;
import cfucli.node.NodeInfo;
import cfucli.relay.Home;

/** Starting the other halves of this tool from the cli: the window, or a headless node when there
 *  is no window to be had. Both write their output to a log under the run directory, because a
 *  process started in the background that fails silently is the thing this project has lost the
 *  most time to. */
public final class Launch {

    public static final Duration UP = Duration.ofSeconds(45), SESSION = Duration.ofSeconds(40);

    public static Path log(String node) {
        return Home.subdir("run").resolve("node-" + node + ".log");
    }

    public static void app(Path jar, String node, List<String> extra) {
        var cmd = new ArrayList<String>();
        if (AppJar.isExe(jar)) {
            cmd.add(jar.toString());   // cfucliapp.exe: jr starts the JVM and the window itself
        } else {
            cmd.add(java(true).toString());
            cmd.addAll(dock());
            cmd.addAll(List.of("-cp", jar.toString(), AppJar.MAIN));
        }
        cmd.addAll(List.of("--host", "--node", node));
        cmd.addAll(extra);
        spawn(cmd, node);
    }

    public static void headless(String node) {
        new NodeClient(node).ensureRunning(null, Self.jar());
    }

    static void spawn(List<String> cmd, String node) {
        try {
            new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log(node).toFile()).start();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start " + cmd.getFirst(), e);
        }
    }

    /** On macOS the Dock shows a JVM as "java" with a coffee cup unless told otherwise, and only
     *  the launch command can tell it - by the time JavaFX starts, the Dock entry exists. The icon
     *  is the one the installer builds from the logo; a machine without it still gets the name. */
    static List<String> dock() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) return List.of();
        var icns = cfucli.relay.Home.file("cfucli.icns");
        return Files.isRegularFile(icns) ? List.of("-Xdock:name=cfucli", "-Xdock:icon=" + icns)
                                         : List.of("-Xdock:name=cfucli");
    }

    /** javaw so a window does not drag a console along behind it; java when it is missing. */
    static Path java(boolean windowless) {
        var bin = Paths.get(System.getProperty("java.home")).resolve("bin");
        var w = bin.resolve(cfucli.relay.Platform.javaExecutable(true));
        return windowless && Files.isRegularFile(w) ? w : bin.resolve(cfucli.relay.Platform.javaExecutable(false));
    }

    public static NodeClient awaitNode(String node, Duration timeout) {
        var c = new NodeClient(node);
        var deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (c.running()) return c;
            sleep(200);
        }
        throw new IllegalStateException("node '" + node + "' never opened its port - see " + log(node));
    }

    /** The window claims the node before it has a session, so being up is not being ready. */
    public static void awaitHostSession(NodeClient c, Duration timeout) {
        var deadline = Instant.now().plus(timeout);
        var last = "";
        while (Instant.now().isBefore(deadline)) {
            try {
                var r = c.call("status", Map.of(), Duration.ofSeconds(10));
                if (r.hasNonNull("host")) return;
            } catch (RuntimeException e) {
                last = " (" + e.getMessage() + ")";
            }
            sleep(250);
        }
        throw new IllegalStateException("node '" + c.node() + "' started but never opened a shell" + last
                                        + " - see " + log(c.node()));
    }

    public static boolean nodeRunning(String node) {
        return NodeInfo.read(node).filter(NodeInfo::processAlive).isPresent();
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Launch() {}
}
