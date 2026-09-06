package cfucli.app;

import module java.base;
import com.techsenger.jeditermfx.core.StyledTextConsumerAdapter;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.model.CharBuffer;
import javafx.application.Platform;
import cfucli.node.PtyHost;

/** Not a junit test: prints the TextStyle every cell actually carries, which is the only way to
 *  tell a theme that is not being applied from a shell that is asking for other colours.
 *  <p>
 *  mvn -o exec:java -Dexec.mainClass=cfucli.app.StyleDump -Dexec.classpathScope=test -Dexec.args="cmd.exe"
 */
public final class StyleDump {

    public static void main(String[] args) throws Exception {
        var shell = args.length > 0 ? args[0] : "cmd.exe";
        var theme = new TerminalTheme(13.5);
        System.out.println("theme foreground = " + theme.getDefaultForeground().toColor()
                           + "   background = " + theme.getDefaultBackground().toColor());
        var done = new CountDownLatch(1);
        Platform.startup(() -> done.countDown());
        done.await();

        var pty = PtyHost.start(shell, Paths.get(System.getProperty("user.home")), 100, 24);
        var built = new CompletableFuture<TerminalView>();
        Platform.runLater(() -> {
            try {
                built.complete(new TerminalView(new PtyTty(pty), 100, 24, 13.5));
            } catch (Throwable t) {
                built.completeExceptionally(t);
            }
        });
        var view = built.get(20, TimeUnit.SECONDS);
        var stage = new AtomicReference<javafx.stage.Stage>();
        // Shown inside a BorderPane exactly as the real window does, because the suspicion is the
        // widening that layout forces on the widget - not the painting itself.
        var withCss = List.of(args).contains("--css");
        var withIcons = List.of(args).contains("--icons");
        System.out.println("probe: css=" + withCss + " icons=" + withIcons);
        Platform.runLater(() -> {
            var s = new javafx.stage.Stage();
            s.setTitle("styledump-probe");
            var root = new javafx.scene.layout.BorderPane();
            root.setCenter(view.pane());
            var scene = new javafx.scene.Scene(root);
            if (withCss) Ui.dress(scene);
            if (withIcons) Ui.icons(s, Icons.HOST_ACCENT);
            s.setScene(scene);
            s.show();
            stage.set(s);
        });
        Thread.sleep(5000);
        dump(view, "before resize");

        Platform.runLater(() -> stage.get().setWidth(stage.get().getWidth() + 260));
        Thread.sleep(3000);
        dump(view, "after widening");

        Thread.sleep(20_000);
        pty.close();
        System.exit(0);
    }

    static void dump(TerminalView view, String when) {
        var panel = view.widget.getTerminalPanel();
        System.out.println("=== " + when + "  " + view.columns() + "x" + view.rows()
                           + "  selection=" + panel.selectionProperty().get() + " ===");
        System.out.println("panel windowForeground=" + panel.getWindowForeground()
                           + "  windowBackground=" + panel.getWindowBackground()
                           + "  canvasFill=" + panel.getBackground());
        view.widget.getTerminalTextBuffer().processHistoryAndScreenLines(0, 8, new StyledTextConsumerAdapter() {
            @Override
            public void consume(int x, int y, TextStyle style, CharBuffer characters, int startRow) {
                var text = characters.toString();
                if (text.isBlank()) return;
                System.out.println("row " + y + " x" + x + "  fg=" + describe(style.getForeground())
                                   + " bg=" + describe(style.getBackground())
                                   + " opts=" + options(style)
                                   + "  [" + (text.length() > 40 ? text.substring(0, 40) : text) + "]");
            }
        });
    }

    static String options(TextStyle style) {
        var on = new ArrayList<String>();
        for (var o : TextStyle.Option.values()) if (style.hasOption(o)) on.add(o.name());
        return on.isEmpty() ? "-" : String.join("+", on);
    }

    static String describe(com.techsenger.jeditermfx.core.TerminalColor c) {
        if (c == null) return "null";
        return c.isIndexed() ? "index" : String.valueOf(c.toColor());
    }

    private StyleDump() {}
}
