package cfucli.record;

import module java.base;
import com.arcadedb.database.Database;
import static datapotter.arcadedbhelper.Query.query;

/** The read half. Everything an agent needs to go back over a session without reading all of it:
 *  the job index first, then a slice, a search, or one job's output.
 *  <p>
 *  MEASURED, not assumed: {@code limit} on the underlying select bounds the rows SCANNED, not the
 *  rows returned - asking for 3 output events out of ten returns two, because the limit truncates
 *  the scan and the stream filter then drops rows from what survived. So the query layer passes
 *  {@link #MAX_SCAN} as a scan guard only and applies every user-facing limit itself. Getting this
 *  wrong is worse than a crash: it answers, plausibly, with less than the truth. */
public final class SessionQueries {

    public static final int DEFAULT_LIMIT = 500, MAX_SCAN = 200_000;

    public static List<SessionRow> sessions(Database db, int limit) {
        var all = new ArrayList<>(query(db, SessionRow.TYPEDEF).limit(MAX_SCAN).list());
        all.sort(Comparator.comparing(SessionRow::startedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return cap(all, limit);
    }

    public static Optional<SessionRow> session(Database db, String sessionId) {
        return query(db, SessionRow.TYPEDEF).eq(SessionRow.$sessionId, sessionId).first();
    }

    public static List<EventRow> events(Database db, String sessionId, Long fromSeq, Long toSeq,
                                        List<String> streams, int limit) {
        var rows = scanEvents(db, sessionId, fromSeq, toSeq, streams);
        return cap(rows, limit);
    }

    public static List<EventRow> tail(Database db, String sessionId, List<String> streams, int n) {
        var rows = scanEvents(db, sessionId, null, null, streams);
        var take = Math.max(1, n);
        return rows.size() <= take ? rows : new ArrayList<>(rows.subList(rows.size() - take, rows.size()));
    }

    /** Ascending by sequence, filtered, with the scan bounded but the result never truncated. */
    public static List<EventRow> scanEvents(Database db, String sessionId, Long fromSeq, Long toSeq,
                                            List<String> streams) {
        var q = query(db, EventRow.TYPEDEF).eq(EventRow.$sessionId, sessionId).limit(MAX_SCAN);
        var wanted = streams == null || streams.isEmpty() ? null : Set.copyOf(streams);
        var rows = new ArrayList<EventRow>();
        for (var e : q) {
            if (wanted != null && !wanted.contains(e.stream())) continue;
            if (fromSeq != null && (e.seq() == null || e.seq() < fromSeq)) continue;
            if (toSeq != null && (e.seq() == null || e.seq() > toSeq)) continue;
            rows.add(e);
        }
        rows.sort(Comparator.comparing(EventRow::seq, Comparator.nullsFirst(Comparator.naturalOrder())));
        return rows;
    }

    public static List<JobRow> jobs(Database db, String sessionId, Integer exitCode, int limit) {
        var rows = new ArrayList<JobRow>();
        for (var j : query(db, JobRow.TYPEDEF).eq(JobRow.$sessionId, sessionId).limit(MAX_SCAN)) {
            if (exitCode != null && !exitCode.equals(j.exitCode())) continue;
            rows.add(j);
        }
        rows.sort(Comparator.comparing(JobRow::startedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        return cap(rows, limit);
    }

    public static List<JobRow> failedJobs(Database db, String sessionId, int limit) {
        var rows = jobs(db, sessionId, null, 0).stream()
                .filter(j -> j.exitCode() == null || j.exitCode() != 0)
                .collect(Collectors.toCollection(ArrayList::new));
        return cap(rows, limit);
    }

    public static Optional<JobRow> job(Database db, String jobId) {
        return query(db, JobRow.TYPEDEF).eq(JobRow.$jobId, jobId).first();
    }

    public static List<EventRow> jobEvents(Database db, String jobId, List<String> streams, int limit) {
        var wanted = streams == null || streams.isEmpty() ? null : Set.copyOf(streams);
        var rows = new ArrayList<EventRow>();
        for (var e : query(db, EventRow.TYPEDEF).eq(EventRow.$jobId, jobId).limit(MAX_SCAN)) {
            if (wanted == null || wanted.contains(e.stream())) rows.add(e);
        }
        rows.sort(Comparator.comparing(EventRow::seq, Comparator.nullsFirst(Comparator.naturalOrder())));
        return cap(rows, limit);
    }

    public static long nextSequence(Database db, String sessionId) {
        var max = -1L;
        for (var e : query(db, EventRow.TYPEDEF).eq(EventRow.$sessionId, sessionId).limit(MAX_SCAN)) {
            if (e.seq() != null && e.seq() > max) max = e.seq();
        }
        return max + 1;
    }

    public static SessionSummary summary(Database db, String sessionId) {
        var s = session(db, sessionId).orElse(null);
        var bytes = new LinkedHashMap<String, Long>();
        var counts = new LinkedHashMap<String, Long>();
        var redacted = 0L;
        var events = 0L;
        for (var e : query(db, EventRow.TYPEDEF).eq(EventRow.$sessionId, sessionId).limit(MAX_SCAN)) {
            events++;
            var stream = e.stream() == null ? "unknown" : e.stream();
            bytes.merge(stream, (long) (e.bytes() == null ? 0 : e.bytes()), Long::sum);
            counts.merge(stream, 1L, Long::sum);
            if (Boolean.TRUE.equals(e.redacted())) redacted++;
        }
        var jobs = jobs(db, sessionId, null, 0);
        var failed = jobs.stream().filter(j -> j.exitCode() != null && j.exitCode() != 0).count();
        return new SessionSummary(sessionId,
                s == null ? null : s.role(), s == null ? null : s.hostName(),
                s == null ? null : s.shell(), s == null ? null : s.transport(),
                s == null ? null : s.startedAt(), s == null ? null : s.endedAt(),
                s == null ? null : s.endReason(),
                events, jobs.size(), failed, redacted, bytes, counts);
    }

    /** A limit of zero or less means "no limit", which is how the callers ask for everything. */
    static <T> List<T> cap(List<T> rows, int limit) {
        return limit <= 0 || rows.size() <= limit ? rows : new ArrayList<>(rows.subList(0, limit));
    }

    private SessionQueries() {}
}
