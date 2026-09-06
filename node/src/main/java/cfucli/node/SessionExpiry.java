package cfucli.node;

import module java.base;
import cfucli.relay.Settings;

/** A channel into someone's machine that never closes itself is a backdoor whatever it was built
 *  for, so a shared session ends on its own: after fifteen quiet minutes, and after four hours
 *  whatever is happening. Both numbers are settings.
 *  <p>
 *  A LOCAL-ONLY session is exempt, deliberately. Nothing about it is reachable from off this
 *  machine - no relay, no announcement, no credentials read - so there is no remote party whose
 *  access needs a time limit, and an agent's console has every right to sit untouched while the
 *  agent thinks. Expiring those would be a mystifying failure bought for no safety at all.
 *  <p>
 *  Output alone does not count as activity. A runaway process printing to a shared shell must not
 *  be able to hold a remote channel open for ever; a job actually running does count, so a build
 *  someone started and is waiting on is not cut off underneath them. */
public final class SessionExpiry implements AutoCloseable {

    public static final Duration CHECK = Duration.ofSeconds(20), WARN_BEFORE = Duration.ofMinutes(2);

    /** A test hook, and it earns its place: a fifteen-minute idle limit and a four-hour lifetime
     *  cannot otherwise be watched actually happening, and an unwatched limit is one that has never
     *  been shown to fire. Seconds, idle first, either may be 0 to turn that one off. */
    public static final String OVERRIDE = "CFUCLI_EXPIRY_SECONDS";

    final HostSession session;
    final Duration idle, max, check;
    final Instant startedAt = Instant.now();

    volatile boolean closed, warned;
    Thread thread;

    SessionExpiry(HostSession session, Duration idle, Duration max) {
        this.session = session;
        this.idle = idle;
        this.max = max;
        var shortest = idle == null ? max : max == null ? idle : (idle.compareTo(max) < 0 ? idle : max);
        var half = shortest.dividedBy(2);
        this.check = half.compareTo(CHECK) < 0 ? (half.toMillis() < 1000 ? Duration.ofSeconds(1) : half) : CHECK;
    }

    /** Null when nothing is to be enforced, which is the local-only case and the case where both
     *  numbers have been turned off. */
    static SessionExpiry start(HostSession session, Settings settings) {
        if (session.localOnly()) return null;
        var forced = override();
        var idle = forced != null ? forced[0] : minutes(settings == null ? null : settings.idleTimeoutMinutes());
        var max = forced != null ? forced[1] : hours(settings == null ? null : settings.maxSessionHours());
        if (idle == null && max == null) return null;
        var e = new SessionExpiry(session, idle, max);
        e.thread = Thread.ofPlatform().name("session-expiry").daemon().start(e::watch);
        session.note("this session ends after " + describe(idle, max));
        return e;
    }

    static Duration[] override() {
        var raw = System.getenv(OVERRIDE);
        if (raw == null || raw.isBlank()) return null;
        var parts = raw.split(",");
        return new Duration[]{seconds(parts, 0), seconds(parts, 1)};
    }

    static Duration seconds(String[] parts, int i) {
        if (i >= parts.length) return null;
        try {
            var n = Long.parseLong(parts[i].trim());
            return n <= 0 ? null : Duration.ofSeconds(n);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    void watch() {
        while (!closed) {
            HostSession.sleep(check);
            if (closed) return;
            var now = Instant.now();
            if (max != null && startedAt.plus(max).isBefore(now)) {
                fire("reached its maximum lifetime of " + text(max));
                return;
            }
            if (idle == null) continue;
            if (session.busy()) {
                warned = false;
                continue;
            }
            var quiet = Duration.between(session.lastActivity(), now);
            if (quiet.compareTo(idle) >= 0) {
                fire("was idle for " + text(idle));
                return;
            }
            if (!warned && idle.compareTo(WARN_BEFORE) > 0 && quiet.compareTo(idle.minus(WARN_BEFORE)) >= 0) {
                warned = true;
                session.note("nothing has happened for " + text(quiet) + " - this session ends in about "
                             + text(idle.minus(quiet)) + " unless something does");
            }
        }
    }

    void fire(String why) {
        closed = true;
        session.expire("session " + why);
    }

    String summary() {
        return describe(idle, max);
    }

    static String describe(Duration idle, Duration max) {
        if (idle == null) return text(max);
        if (max == null) return text(idle) + " idle";
        return text(idle) + " idle, or " + text(max) + " whatever happens";
    }

    static String text(Duration d) {
        if (d.toSeconds() < 60) return Math.max(1, d.toSeconds()) + "s";
        var minutes = d.toMinutes();
        if (minutes < 60) return minutes + (minutes == 1 ? " minute" : " minutes");
        var hours = d.toHours();
        var left = minutes - hours * 60;
        return hours + (hours == 1 ? " hour" : " hours") + (left == 0 ? "" : " " + left + "m");
    }

    static Duration minutes(Integer n) {
        return n == null || n <= 0 ? null : Duration.ofMinutes(n);
    }

    static Duration hours(Integer n) {
        return n == null || n <= 0 ? null : Duration.ofHours(n);
    }

    /** Never interrupts the caller, and that is not a nicety. The close that follows an expiry runs
     *  ON this thread - watch, fire, expire, the node's end, the session's close, back here - so an
     *  unconditional interrupt set the flag on the very thread that still had the recorder, the
     *  relay and the pty to shut down. Everything after it failed: the session's end never
     *  committed, so the recording said "live" about a session that had just been closed for being
     *  idle, and the relay's goodbye was interrupted on its way out. */
    @Override
    public void close() {
        closed = true;
        var t = thread;
        if (t != null && t != Thread.currentThread()) t.interrupt();
    }
}
