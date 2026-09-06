package cfucli.app;

import module java.base;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.*;

import static luvjfx.Fx.*;

/** Opening the relay is not instant - a warm connection costs over a second, and the native
 *  transport can sit on a ten-second connect timeout before falling back to REST. Doing that on
 *  the FX thread gives a window that is painted and frozen, which reads as a crash.
 *  <p>
 *  So it happens on a thread, behind something that says what is being waited for. */
public final class Busy {

    public static <T> void run(String title, String detail, Supplier<T> work,
                               Consumer<T> done, Consumer<Throwable> failed) {
        var stage = new Stage(StageStyle.UTILITY);
        var status = label(detail).styleClass("subtitle");
        var spinner = new ProgressIndicator();
        spinner.setPrefSize(22, 22);
        var root = hbox($ -> $.spacing(14).padding(18)).nodes(
                fx(spinner),
                vbox($ -> $.spacing(4)).nodes(label(title).styleClass("card-title"), status));
        var scene = scene(root);
        Ui.dress(scene);
        Ui.icons(stage, Icons.TRAY_ACCENT);
        stage.setTitle("cfucli");
        stage.setScene(scene);
        stage.setOnCloseRequest(javafx.event.Event::consume);
        stage.show();

        Thread.ofPlatform().name("busy").daemon().start(() -> {
            try {
                var value = work.get();
                Platform.runLater(() -> {
                    stage.close();
                    done.accept(value);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    stage.close();
                    failed.accept(t);
                });
            }
        });
    }

    private Busy() {}
}
