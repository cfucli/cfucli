package cfucli.node;

import module java.base;
import java.util.function.Supplier;

/** Old recordings are dropped on a timer rather than only when someone remembers to ask.
 *  <p>
 *  A transcript is useful for days and after that it is mostly somewhere for a password someone
 *  typed to sit unnoticed - which is why the user asked for automatic cleanup rather than only a
 *  verb. What it last did is reported in {@code status}, because a timer whose work is invisible
 *  cannot be told from one that is not running. */
public final class Housekeeping implements AutoCloseable {

    public static final Duration FIRST_RUN = Duration.ofMinutes(1), THEN_EVERY = Duration.ofHours(6);

    /** Seconds, first run then interval - the same kind of test hook as {@link SessionExpiry#OVERRIDE},
     *  and there for the same reason: a six-hour timer is otherwise never seen to fire. */
    public static final String OVERRIDE = "CFUCLI_HOUSEKEEPING_SECONDS";

    final Supplier<String> pass;
    final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "housekeeping");
        t.setDaemon(true);
        return t;
    });

    volatile long lastRunAt;
    volatile String lastResult = "not run yet";
    volatile long runs;

    Housekeeping(Supplier<String> pass, Duration first, Duration every) {
        this.pass = pass;
        timer.scheduleWithFixedDelay(this::run, first.toMillis(), every.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Not immediately: a node is started to answer a question, and the answer should not wait on a
     *  sweep of the store. A minute later nobody is watching. */
    public static Housekeeping start(Supplier<String> pass) {
        var forced = override();
        return new Housekeeping(pass, forced == null ? FIRST_RUN : forced[0],
                forced == null ? THEN_EVERY : forced[1]);
    }

    static Duration[] override() {
        var raw = System.getenv(OVERRIDE);
        if (raw == null || raw.isBlank()) return null;
        var parts = raw.split(",");
        var first = SessionExpiry.seconds(parts, 0);
        var every = SessionExpiry.seconds(parts, 1);
        if (first == null) return null;
        return new Duration[]{first, every == null ? first : every};
    }

    void run() {
        runs++;
        lastRunAt = System.currentTimeMillis();
        try {
            var said = pass.get();
            lastResult = said == null || said.isBlank() ? "nothing to drop" : said;
            if (said != null && !said.isBlank()) System.out.println("[node] " + said);
        } catch (RuntimeException e) {
            lastResult = "failed: " + e;
            System.err.println("[node] housekeeping failed: " + e);
        }
    }

    public long lastRunAt() {
        return lastRunAt;
    }

    public String lastResult() {
        return lastResult;
    }

    public long runs() {
        return runs;
    }

    @Override
    public void close() {
        timer.shutdownNow();
    }
}
