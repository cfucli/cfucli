package cfucli.app;

import module java.base;
import java.awt.image.BufferedImage;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javax.imageio.ImageIO;

/** {@link IconSheet} written to a PNG instead of shown in a window, so the icons can be looked at
 *  from a file - every role in a row, every size at 1:1 and again at 4x. The background is the
 *  mid grey a taskbar tends to be, which is where contrast problems show.
 *  <p>
 *  mvn exec:java -Dexec.mainClass=cfucli.app.IconPng -Dexec.classpathScope=test -Dexec.args=out.png */
public final class IconPng {

    public static void main(String[] args) throws Exception {
        var out = Paths.get(args.length > 0 ? args[0] : "icons.png");
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();
        var done = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            try {
                write(out);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        if (failure.get() != null) throw new IllegalStateException(failure.get());
        System.out.println("wrote " + out.toAbsolutePath());
    }

    static void write(Path out) throws IOException {
        var roles = List.of(Icons.HOST_ACCENT, Icons.VIEWER_ACCENT, Icons.TRAY_ACCENT);
        int[] sizes = {16, 24, 32, 48, 64, 128};
        var width = 12;
        for (var s : sizes) width += s * 4 + 14;
        var rowH = 12 + 128 + 10 + 128 * 4 + 12;
        var sheet = new BufferedImage(width, rowH * roles.size(), BufferedImage.TYPE_INT_ARGB);
        var g = sheet.createGraphics();
        g.setColor(new java.awt.Color(0x7A7F94));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (var r = 0; r < roles.size(); r++) {
            var x = 12;
            var y = 12 + r * rowH;
            for (var s : sizes) {
                var img = Icons.awt(s, roles.get(r));
                g.drawImage(img, x, y, null);
                g.drawImage(img, x, y + 138, s * 4, s * 4, null);
                x += s * 4 + 14;
            }
        }
        g.dispose();
        ImageIO.write(sheet, "png", out.toFile());
    }

    private IconPng() {}
}
