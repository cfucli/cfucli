package cfucli.record;

import module java.base;
import com.arcadedb.database.Database;
import xyz.jphil.arcadedb.datahelper.Delete;
import static xyz.jphil.arcadedb.datahelper.Query.query;

/** Recordings do not accumulate for ever. A session's transcript is useful for days, and after
 *  that it is mostly a place for a secret to sit unnoticed. */
public final class Retention {

    public static long forget(Database db, String sessionId) {
        return Tx.get(db, () -> {
            var events = Delete.matching(query(db, EventRow.TYPEDEF).eq(EventRow.$sessionId, sessionId));
            Delete.matching(query(db, JobRow.TYPEDEF).eq(JobRow.$sessionId, sessionId));
            Delete.matching(query(db, SessionRow.TYPEDEF).eq(SessionRow.$sessionId, sessionId));
            return events;
        });
    }

    public static int forgetOlderThan(Database db, int days) {
        return forgetOlderThan(db, days, Set.of());
    }

    /** {@code keep} is the session or sessions running right now. A node that has been up longer
     *  than the retention window would otherwise delete the recording it is in the middle of
     *  writing, and every later answer about that session would be a lie. */
    public static int forgetOlderThan(Database db, int days, Set<String> keep) {
        var cutoff = System.currentTimeMillis() - Duration.ofDays(Math.max(0, days)).toMillis();
        var sessions = query(db, SessionRow.TYPEDEF).lt(SessionRow.$startedAt, cutoff).list()
                .stream().filter(s -> !keep.contains(s.sessionId())).toList();
        sessions.forEach(s -> forget(db, s.sessionId()));
        return sessions.size();
    }

    /** Drops the recorded text but keeps the shape of the session - counts, timings, exit codes.
     *  For when the transcript is sensitive but the audit trail should survive. */
    public static int scrub(Database db, String sessionId) {
        var rows = query(db, EventRow.TYPEDEF).eq(EventRow.$sessionId, sessionId).list();
        Tx.run(db, () -> {
            for (var e : rows) {
                e.text(Redactor.MASK).plain(Redactor.MASK).redacted(true)
                        .in(db).whereEq(EventRow.$eventId, e.eventId()).upsert();
            }
        });
        return rows.size();
    }

    private Retention() {}
}
