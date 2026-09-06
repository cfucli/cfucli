package cfucli.node;

import module java.base;
import java.util.function.Consumer;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

/** The one real shell. Everything that wants to see it subscribes; everything that wants to type
 *  into it calls write. That is what makes the session shared rather than mirrored - the local
 *  window and the remote end are two subscribers to one process, not two processes. */
public final class PtyHost implements AutoCloseable {

    public static final int DEFAULT_COLUMNS = 120, DEFAULT_ROWS = 30;

    final PtyProcess process;
    final String command;
    final List<Consumer<byte[]>> listeners = new CopyOnWriteArrayList<>();
    final Thread pump;

    volatile boolean running = true;

    PtyHost(PtyProcess process, String command) {
        this.process = process;
        this.command = command;
        this.pump = Thread.ofPlatform().name("pty-read").daemon().start(this::pump);
    }

    public static PtyHost start(String shell, Path cwd, int columns, int rows) {
        try {
            var env = new HashMap<>(System.getenv());
            env.put("TERM", "xterm-256color");
            var argv = Argv.split(shell);
            var p = new PtyProcessBuilder()
                    .setCommand(argv)
                    .setDirectory((cwd == null ? Paths.get(System.getProperty("user.home")) : cwd).toString())
                    .setEnvironment(env)
                    .setInitialColumns(columns <= 0 ? DEFAULT_COLUMNS : columns)
                    .setInitialRows(rows <= 0 ? DEFAULT_ROWS : rows)
                    .setConsole(false)
                    .setUseWinConPty(true)
                    .start();
            var host = new PtyHost(p, shell);
            host.normalise(argv[0]);
            return host;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start " + shell, e);
        }
    }

    /** Starts the shared shell on a clean screen in UTF-8.
     *  <p>
     *  UTF-8 first, or every Devanagari and box-drawing character arrives as mojibake. Then a
     *  clear, for two reasons: a shell someone is about to watch should not open on a version
     *  banner and a Clink update nag, and the clear also settles a repaint quirk in jeditermfx
     *  1.1.0 where the very first screenful is painted with the foreground and background the
     *  wrong way round. That second reason is a workaround, not a diagnosis - the cell styles are
     *  provably plain (no colour, no inverse, no selection), so the fault is in the painting and
     *  has not been pinned down. */
    void normalise(String exe) {
        var name = exe.toLowerCase();
        if (name.contains("cmd")) write("chcp 65001 > nul && cls\r\n".getBytes(StandardCharsets.US_ASCII));
        else if (name.contains("powershell") || name.contains("pwsh")) write("Clear-Host\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    public String command() {
        return command;
    }

    public void onOutput(Consumer<byte[]> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<byte[]> listener) {
        listeners.remove(listener);
    }

    public void write(byte[] bytes) {
        try {
            var out = process.getOutputStream();
            synchronized (this) {
                out.write(bytes);
                out.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write to the shell", e);
        }
    }

    public void resize(int columns, int rows) {
        if (process.isAlive()) process.setWinSize(new WinSize(columns, rows));
    }

    public boolean alive() {
        return process.isAlive();
    }

    public Integer exitCode() {
        return process.isAlive() ? null : process.exitValue();
    }

    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    void pump() {
        var buf = new byte[16 * 1024];
        try (var in = process.getInputStream()) {
            while (running) {
                var n = in.read(buf);
                if (n < 0) return;
                if (n == 0) continue;
                var chunk = Arrays.copyOf(buf, n);
                for (var l : listeners) {
                    try {
                        l.accept(chunk);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        running = false;
        listeners.clear();
        process.destroy();
        pump.interrupt();
    }
}
