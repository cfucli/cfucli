package cfucli.app;

import module java.base;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import cfucli.node.NodeService;

/** One exe. Host shares this machine's shell, Viewer mirrors one and types into it, Tray manages
 *  whatever is running here. The role comes from the command line when something else starts it,
 *  and from the launcher window when a person does. */
public final class CfuCliApp extends Application {

    @Override
    public void start(Stage stage) {
        // Every exit path in this app is explicit, because closing a window has to end a session
        // and remove a port file, not merely stop painting.
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> report(e));
        open(stage, AppArgs.parse(getParameters().getRaw()));
    }

    void open(Stage stage, AppArgs args) {
        // --join with nothing to join with is not an error worth an exception: the launcher already
        // has the two boxes it would have to ask for anyway.
        var role = args.role() == AppRole.JOIN && !args.readyToJoin() ? AppRole.ASK : args.role();
        switch (role) {
            case TRAY -> new TrayRole().start();
            case ASK -> new LauncherWindow(stage, args, next -> open(new Stage(), next));
            case HOST, JOIN -> session(stage, args);
        }
    }

    void session(Stage stage, AppArgs args) {
        var host = claim(args);
        if (host == null) return;
        var service = host.service();
        service.consent(new FxConsentGate(() -> stage.isShowing() ? stage : null));
        service.window(new StageControl(stage));
        // "cfucli node stop" ends the node inside this process. Without this the window stays
        // open over a node that no longer exists, which reads as a live session and is not one.
        service.alsoOnShutdown(() -> Platform.runLater(() -> Shutdown.now(null, null)));

        var joining = args.role() == AppRole.JOIN;
        Busy.run(joining ? "Connecting to " + args.sessionId() : "Starting a shared shell",
                joining ? "Reaching the relay and agreeing keys with the host."
                        : args.local() ? "Opening a shell on this machine only. The relay is not involved."
                        : "Opening the relay and announcing this machine.",
                () -> begin(service, args),
                ignored -> build(stage, host, joining),
                failure -> {
                    host.close();
                    Dialogs.error(null, joining ? "Could not join that session" : "Could not start sharing",
                            Dialogs.reason(failure));
                    open(new Stage(), new AppArgs(AppRole.ASK, args.node(), args.shell(),
                            args.sessionId(), args.password(), args.local()));
                });
    }

    Object begin(NodeService service, AppArgs args) {
        if (args.role() == AppRole.JOIN) {
            return service.dispatch("join", cfucli.node.Wire.request("join", Map.of(
                    "sessionId", args.sessionId(), "password", args.password())).get("args"));
        }
        var a = new LinkedHashMap<String, Object>();
        if (args.shell() != null) a.put("shell", args.shell());
        if (args.local()) a.put("local", true);
        return service.dispatch("host", cfucli.node.Wire.request("host", a).get("args"));
    }

    void build(Stage stage, NodeHost host, boolean joining) {
        try {
            if (joining) new ViewerWindow(stage, host);
            else new HostWindow(stage, host);
        } catch (RuntimeException e) {
            report(e);
            Dialogs.error(null, "The window could not be built", Dialogs.reason(e));
            Shutdown.now(host, null);
        }
    }

    /** Takes the node name, or explains why it cannot and offers the two honest ways out. */
    NodeHost claim(AppArgs args) {
        try {
            return NodeHost.open(args.node());
        } catch (NodeBusy busy) {
            var free = NodeHost.freeName(args.node());
            var choice = Dialogs.choose(null, "That node name is taken",
                    busy.getMessage() + ". A node holds its session history exclusively, so two cannot "
                    + "share a name. If the cli started that one it has no window of its own, and stopping "
                    + "it ends any session it is serving.",
                    List.of("Use '" + free + "' instead", "Stop it and take the name", "Cancel"),
                    Icons.TRAY_ACCENT);
            return switch (choice) {
                case 0 -> claim(new AppArgs(args.role(), free, args.shell(), args.sessionId(), args.password(), args.local()));
                case 1 -> {
                    NodeHost.evict(busy.held, Duration.ofSeconds(20));
                    yield NodeHost.open(args.node());
                }
                default -> {
                    Shutdown.now(null, null);
                    yield null;
                }
            };
        }
    }

    static void report(Throwable e) {
        System.err.println("[cfucli-app] " + e);
        e.printStackTrace();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
