package cfucli.app;

import module java.base;
import cfucli.node.PtyHost;

/** Not a junit test: a main you run when the terminal renders wrongly and you need to see the
 *  actual escape sequences a shell emits rather than guess at them.
 *  <p>
 *  mvn -o exec:java -Dexec.mainClass=cfucli.app.PtyDump -Dexec.classpathScope=test -Dexec.args="cmd.exe"
 */
public final class PtyDump {

    static final char ESC = 27;

    public static void main(String[] args) throws Exception {
        var shell = args.length > 0 ? args[0] : "cmd.exe";
        var sink = new StringBuilder();
        var pty = PtyHost.start(shell, Paths.get(System.getProperty("user.home")), 120, 30);
        pty.onOutput(b -> {
            synchronized (sink) {
                sink.append(new String(b, StandardCharsets.UTF_8));
            }
        });
        Thread.sleep(4000);
        pty.write("echo hello\r\n".getBytes(StandardCharsets.UTF_8));
        Thread.sleep(2500);
        pty.close();
        var text = sink.toString();
        System.out.println("=== " + text.length() + " chars ===");
        System.out.println(visible(text));
        System.out.println("=== control sequences seen ===");
        var seen = new LinkedHashMap<String, Integer>();
        var m = Pattern.compile(ESC + "\\[([0-9;?]*)([A-Za-z])").matcher(text);
        while (m.find()) seen.merge("ESC[" + m.group(1) + m.group(2), 1, Integer::sum);
        seen.forEach((k, v) -> System.out.println("  " + k + "  x" + v));
        System.exit(0);
    }

    static String visible(String s) {
        var b = new StringBuilder();
        for (var c : s.toCharArray()) {
            if (c == ESC) b.append("<ESC>");
            else if (c == '\r') b.append("<CR>");
            else if (c == '\n') b.append("<LF>").append('\n');
            else if (c < 32) b.append('<').append((int) c).append('>');
            else b.append(c);
        }
        return b.toString();
    }

    private PtyDump() {}
}
