package cfucli.app;

import module java.base;
import java.util.function.Supplier;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.*;
import cfucli.node.ConsentGate;
import cfucli.relay.Ids;

import static luvjfx.Fx.*;

/** RustDesk's prompt, on the machine being shared. Asked once per viewer arrival, never per
 *  command - per-command approval would make agentic use unusable and would train the human to
 *  click yes without reading.
 *  <p>
 *  Silence means no. A prompt that opens the channel when nobody is at the keyboard is a backdoor
 *  however it was meant, and this is a tool for a machine that has been compromised once. */
public final class FxConsentGate implements ConsentGate {

    public static final Duration LIMIT = Duration.ofMinutes(2);

    final Supplier<Window> owner;
    final Duration limit;
    final AtomicReference<Stage> open = new AtomicReference<>();

    public FxConsentGate(Supplier<Window> owner) {
        this(owner, LIMIT);
    }

    public FxConsentGate(Supplier<Window> owner, Duration limit) {
        this.owner = owner;
        this.limit = limit;
    }

    @Override
    public boolean allow(String sessionId, String detail) {
        var answer = new CompletableFuture<Boolean>();
        Platform.runLater(() -> ask(sessionId, detail, answer));
        try {
            return answer.get(limit.toSeconds() + 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Platform.runLater(this::dismiss);
            return false;
        } catch (TimeoutException | ExecutionException e) {
            Platform.runLater(this::dismiss);
            return false;
        }
    }

    void dismiss() {
        var s = open.getAndSet(null);
        if (s != null) s.close();
    }

    void ask(String sessionId, String detail, CompletableFuture<Boolean> answer) {
        var stage = new Stage(StageStyle.UTILITY);
        open.set(stage);
        var left = new AtomicLong(limit.toSeconds());
        var countdown = label("").styleClass("subtitle");

        var root = vbox($ -> $.spacing(12).padding(18)).nodes(
                label("Someone is joining this machine").styleClass("title"),
                label(detail).styleClass("subtitle"),
                label("session " + Ids.prettySessionId(sessionId)).styleClass("mono"),
                wrapped("They will see this shell and be able to type into it. You keep typing too, "
                        + "and F2 locks them out at any moment."),
                countdown,
                hbox($ -> $.spacing(10)).nodes(
                        button("Allow  (Y)").styleClass("primary").onAction(e -> decide(stage, answer, true)),
                        button("Refuse  (N)").styleClass("danger").onAction(e -> decide(stage, answer, false))));

        var scene = scene(root);
        Ui.dress(scene);
        // A pure menu with no text input, which is the one case bare letters are legitimate.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (Keys.plain(e, KeyCode.Y) || Keys.plain(e, KeyCode.ENTER)) {
                e.consume();
                decide(stage, answer, true);
            } else if (Keys.plain(e, KeyCode.N) || Keys.plain(e, KeyCode.ESCAPE)) {
                e.consume();
                decide(stage, answer, false);
            }
        });

        var w = owner.get();
        if (w != null) {
            stage.initOwner(w);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        Ui.icons(stage, Icons.HOST_ACCENT);
        stage.setTitle("cfucli - allow access?");
        stage.setScene(scene);
        // Deliberately NOT alwaysOnTop. It is owned by the window being joined and window-modal,
        // so it already sits above that window and opens on the monitor that window is on. Made
        // global-topmost it jumped screens and covered whatever the person was actually working
        // on, which is a poor way to ask a question about a session on another monitor.
        stage.setOnCloseRequest(e -> decide(stage, answer, false));

        var ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> {
            var n = left.decrementAndGet();
            countdown.text(waiting(n));
            if (n <= 0) decide(stage, answer, false);
        }));
        ticker.setCycleCount(Animation.INDEFINITE);
        countdown.text(waiting(left.get()));
        ticker.play();
        stage.setOnHidden(e -> {
            ticker.stop();
            answer.complete(false);
        });
        stage.show();
    }

    void decide(Stage stage, CompletableFuture<Boolean> answer, boolean allowed) {
        open.compareAndSet(stage, null);
        answer.complete(allowed);
        stage.close();
    }

    static String waiting(long seconds) {
        return "Refusing automatically in " + Math.max(0, seconds) + "s if nobody answers.";
    }

    static luvjfx.FxLabel wrapped(String text) {
        return label(text).styleClass("subtitle").attr(l -> {
            l.setWrapText(true);
            l.setMaxWidth(430);
        });
    }
}
