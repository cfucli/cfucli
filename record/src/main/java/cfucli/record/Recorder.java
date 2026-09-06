package cfucli.record;

import module java.base;
import com.arcadedb.database.Database;
import static xyz.jphil.arcadedb.datahelper.Query.query;

/** The write half. One instance per live session, held by the node.
 *  <p>
 *  Sequence numbers continue across a restart rather than starting again, because a rejoin is the
 *  normal case here and two events numbered 7 in one session would make every later answer a lie. */
public final class Recorder {

    final Database db;
    final String sessionId, role;
    final AtomicLong seq;

    volatile boolean recordOutput = true, paused = false;

    Recorder(Database db, String sessionId, String role, long startSeq) {
        this.db = db;
        this.sessionId = sessionId;
        this.role = role;
        this.seq = new AtomicLong(startSeq);
    }

    public static Recorder open(Database db, String sessionId, String role, String hostName, String shell,
                                String transport, boolean recordOutput) {
        var next = SessionQueries.nextSequence(db, sessionId);
        Tx.run(db, () -> new SessionRow().sessionId(sessionId).role(role).hostName(hostName).shell(shell)
                .transport(transport).startedAt(System.currentTimeMillis())
                .in(db).whereEq(SessionRow.$sessionId, sessionId).upsert());
        var r = new Recorder(db, sessionId, role, next);
        r.recordOutput = recordOutput;
        return r;
    }

    public String sessionId() {
        return sessionId;
    }

    public boolean paused() {
        return paused;
    }

    public void paused(boolean p) {
        paused = p;
        if (p) control("recording paused"); else control("recording resumed");
    }

    public void recordOutput(boolean b) {
        recordOutput = b;
    }

    public long event(String stream, String origin, String text, String jobId) {
        if (paused && !Streams.CONTROL.equals(stream)) return -1;
        if (!recordOutput && isOutput(stream)) return -1;
        // Terminated here rather than by the caller. control() and error() used to do it and the
        // one caller that reached event() directly did not, which is how most of the tool's own
        // notes came to be recorded unterminated and to run together when read back. An invariant
        // that every call site has to remember is one the next call site will forget.
        var noted = Streams.isNote(stream) ? line(text) : text;
        var redacted = Streams.INPUT.equals(stream) && Redactor.wouldRedact(noted);
        var stored = redacted ? Redactor.apply(noted) : noted;
        var n = seq.getAndIncrement();
        Tx.run(db, () -> new EventRow().eventId(EventRow.id(sessionId, n)).sessionId(sessionId).seq(n)
                .ts(System.currentTimeMillis()).stream(stream).origin(origin).jobId(jobId)
                .text(stored).plain(Ansi.strip(stored))
                .bytes(stored == null ? 0 : stored.getBytes(StandardCharsets.UTF_8).length)
                .redacted(redacted)
                .in(db).insert());
        return n;
    }

    public long output(String origin, String text) {
        return event(Streams.OUTPUT, origin, text, null);
    }

    public long input(String origin, String text) {
        return event(Streams.INPUT, origin, text, null);
    }

    /** Control and error notes are whole lines. Without the terminator two adjacent notes run
     *  together in the flattened line view that grep works over, and the search misses both.
     *  The terminator itself is applied by {@link #event}, so writing one by any other door is
     *  equally safe. */
    public long control(String text) {
        return event(Streams.CONTROL, role, text, null);
    }

    public long error(String text) {
        return event(Streams.ERROR, role, text, null);
    }

    static String line(String text) {
        return text == null || text.endsWith("\n") ? text : text + "\n";
    }

    public JobRow startJob(String jobId, String command, String workingDir, String origin) {
        var job = new JobRow().jobId(jobId).sessionId(sessionId).command(command).workingDir(workingDir)
                .origin(origin).startedAt(System.currentTimeMillis()).state("running")
                .stdoutBytes(0L).stderrBytes(0L);
        Tx.run(db, () -> job.in(db).whereEq(JobRow.$jobId, jobId).upsert());
        control("exec " + jobId + ": " + Redactor.apply(command));
        return job;
    }

    public void jobOutput(String jobId, String stream, String text) {
        event(stream, "host", text, jobId);
    }

    public void finishJob(String jobId, Integer exitCode, long stdoutBytes, long stderrBytes, String state) {
        Tx.run(db, () -> {
            var existing = query(db, JobRow.TYPEDEF).eq(JobRow.$jobId, jobId).firstOrNull();
            var job = existing == null ? new JobRow().jobId(jobId).sessionId(sessionId) : existing;
            job.endedAt(System.currentTimeMillis()).exitCode(exitCode)
                    .stdoutBytes(stdoutBytes).stderrBytes(stderrBytes).state(state)
                    .in(db).whereEq(JobRow.$jobId, jobId).upsert();
        });
        control("exec " + jobId + " " + state + (exitCode == null ? "" : " exit=" + exitCode));
    }

    public void end(String reason) {
        control("session ended: " + reason);
        Tx.run(db, () -> {
            var s = query(db, SessionRow.TYPEDEF).eq(SessionRow.$sessionId, sessionId).firstOrNull();
            if (s == null) return;
            s.endedAt(System.currentTimeMillis()).endReason(reason)
                    .in(db).whereEq(SessionRow.$sessionId, sessionId).upsert();
        });
    }

    static boolean isOutput(String stream) {
        return Streams.OUTPUT.equals(stream) || Streams.EXEC_STDOUT.equals(stream) || Streams.EXEC_STDERR.equals(stream);
    }
}
