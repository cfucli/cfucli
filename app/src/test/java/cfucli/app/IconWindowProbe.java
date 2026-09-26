package cfucli.app;

import module java.base;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** Two windows side by side: one with the drawn icons exactly as the app sets them, one with a
 *  bitmap loaded from a file. If the file one shows its icon in the title bar and the drawn one
 *  does not, the fault is in how the drawn images reach Windows, not in the window.
 *  <p>
 *  mvn exec:java -Dexec.mainClass=cfucli.app.IconWindowProbe -Dexec.classpathScope=test -Dexec.args=path/to/icon.png */
public final class IconWindowProbe {

    public static void main(String[] args) throws Exception {
        var png = args.length > 0 ? Paths.get(args[0]).toUri().toString() : null;
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();
        Platform.runLater(() -> {
            var drawn = new Stage();
            Ui.icons(drawn, Icons.TRAY_ACCENT);
            drawn.setTitle("probe-drawn");
            drawn.setScene(new Scene(new Label("drawn icons"), 320, 120));
            drawn.setX(100);
            drawn.setY(100);
            drawn.show();
            var icons = drawn.getIcons();
            System.out.println("drawn: " + icons.size() + " icons, first " + icons.getFirst().getWidth() + "px, centre pixel "
                               + Integer.toHexString(icons.getFirst().getPixelReader().getArgb(8, 8)));
            if (png != null) {
                var file = new Stage();
                file.getIcons().setAll(new Image(png));
                file.setTitle("probe-file");
                file.setScene(new Scene(new Label("file icon"), 320, 120));
                file.setX(460);
                file.setY(100);
                file.show();
                System.out.println("file: " + file.getIcons().getFirst().getWidth() + "px, error=" + file.getIcons().getFirst().isError());
            }
        });
        Thread.sleep(Long.getLong("probe.ms", 9000));
        Platform.exit();
    }

    private IconWindowProbe() {}
}
