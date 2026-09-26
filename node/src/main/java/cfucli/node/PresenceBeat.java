package cfucli.node;

import module java.base;
import cfucli.relay.Presence;
import cfucli.relay.RelayTransport;
import cfucli.relay.Settings;

/** Keeps this node's presence entry fresh while {@code presenceAvailable} is on, the same way
 *  {@link HostSession}'s own beat thread keeps a hosted session's meta from going stale - except
 *  this one runs whether or not anything is being hosted right now, because the whole point is
 *  being askable before a session exists.
 *  <p>
 *  Deliberately does NOT watch for incoming requests itself - {@code requests}/{@code approve}/
 *  {@code decline} read the relay live, on demand, which is simpler and needs no background state
 *  to keep in sync with what a human has already answered. Only the heartbeat benefits from
 *  running unattended, because nobody is going to run a command every thirty seconds just to stay
 *  looking alive. */
public final class PresenceBeat implements AutoCloseable {

    static final Duration TICK = Presence.HEARTBEAT_EVERY;

    final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "presence-beat");
        t.setDaemon(true);
        return t;
    });

    volatile String lastError;

    PresenceBeat(Settings settings, String node, java.util.function.Supplier<RelayTransport> relay) {
        timer.scheduleWithFixedDelay(() -> tick(settings, node, relay), 0, TICK.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static PresenceBeat start(Settings settings, String node, java.util.function.Supplier<RelayTransport> relay) {
        return new PresenceBeat(settings, node, relay);
    }

    void tick(Settings settings, String node, java.util.function.Supplier<RelayTransport> relay) {
        if (!Boolean.TRUE.equals(settings.presenceAvailable())) return;
        var identity = settings.identityName();
        if (identity == null || identity.isBlank()) return;
        try {
            Presence.heartbeat(relay.get(), identity, node);
            lastError = null;
        } catch (RuntimeException e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    public String lastError() {
        return lastError;
    }

    @Override
    public void close() {
        timer.shutdownNow();
    }
}
