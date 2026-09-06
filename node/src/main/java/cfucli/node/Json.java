package cfucli.node;

import module java.base;
import com.fasterxml.jackson.databind.node.*;
import cfucli.record.*;
import cfucli.relay.SessionStatus;

/** Row to JSON, in one place, so the cli and any future consumer see one shape. */
public final class Json {

    public static ObjectNode event(EventRow e) {
        var n = Wire.obj();
        n.put("seq", e.seq());
        n.put("ts", e.ts());
        n.put("stream", e.stream());
        n.put("origin", e.origin());
        if (e.jobId() != null) n.put("jobId", e.jobId());
        n.put("bytes", e.bytes() == null ? 0 : e.bytes());
        if (Boolean.TRUE.equals(e.redacted())) n.put("redacted", true);
        n.put("text", e.text());
        n.put("plain", e.plain());
        return n;
    }

    public static ObjectNode job(JobRow j) {
        var n = Wire.obj();
        n.put("jobId", j.jobId());
        n.put("command", j.command());
        n.put("origin", j.origin());
        n.put("state", j.state());
        if (j.workingDir() != null) n.put("cwd", j.workingDir());
        if (j.exitCode() == null) n.putNull("exitCode"); else n.put("exitCode", j.exitCode());
        n.put("startedAt", j.startedAt());
        if (j.endedAt() != null) n.put("endedAt", j.endedAt());
        if (j.durationMillis() != null) n.put("millis", j.durationMillis());
        n.put("stdoutBytes", j.stdoutBytes() == null ? 0 : j.stdoutBytes());
        n.put("stderrBytes", j.stderrBytes() == null ? 0 : j.stderrBytes());
        return n;
    }

    public static ObjectNode match(GrepMatch m) {
        var n = Wire.obj();
        n.put("line", m.lineNumber());
        n.put("seq", m.seq());
        n.put("ts", m.ts());
        n.put("stream", m.stream());
        if (m.jobId() != null) n.put("jobId", m.jobId());
        n.put("text", m.line());
        n.set("before", strings(m.before()));
        n.set("after", strings(m.after()));
        return n;
    }

    public static ObjectNode summary(SessionSummary s) {
        var n = Wire.obj();
        n.put("sessionId", s.sessionId());
        n.put("role", s.role());
        n.put("hostName", s.hostName());
        n.put("shell", s.shell());
        n.put("transport", s.transport());
        n.put("live", s.live());
        if (s.startedAt() != null) n.put("startedAt", s.startedAt());
        if (s.endedAt() != null) n.put("endedAt", s.endedAt());
        if (s.endReason() != null) n.put("endReason", s.endReason());
        if (s.durationMillis() != null) n.put("millis", s.durationMillis());
        n.put("events", s.events());
        n.put("jobs", s.jobs());
        n.put("failedJobs", s.failedJobs());
        n.put("redactedEvents", s.redactedEvents());
        n.put("totalBytes", s.totalBytes());
        n.set("bytesByStream", longs(s.bytesByStream()));
        n.set("eventsByStream", longs(s.eventsByStream()));
        return n;
    }

    public static ObjectNode session(SessionRow s) {
        var n = Wire.obj();
        n.put("sessionId", s.sessionId());
        n.put("role", s.role());
        n.put("hostName", s.hostName());
        n.put("shell", s.shell());
        n.put("transport", s.transport());
        n.put("startedAt", s.startedAt());
        if (s.endedAt() != null) n.put("endedAt", s.endedAt());
        if (s.endReason() != null) n.put("endReason", s.endReason());
        n.put("live", s.endedAt() == null);
        return n;
    }

    public static ObjectNode status(SessionStatus s) {
        var n = Wire.obj();
        n.put("sessionId", s.sessionId());
        n.put("state", s.state().name());
        n.put("detail", s.detail());
        n.put("usable", s.state().usable());
        if (s.hostName() != null) n.put("hostName", s.hostName());
        if (s.shell() != null) n.put("shell", s.shell());
        if (s.startedAt() != null) n.put("startedAt", s.startedAt().toEpochMilli());
        if (s.lastBeat() != null) n.put("lastBeat", s.lastBeat().toEpochMilli());
        n.put("locked", s.locked());
        n.put("viewOnly", s.viewOnly());
        return n;
    }

    public static ObjectNode exec(ExecResult r) {
        var n = Wire.obj();
        n.put("jobId", r.jobId());
        if (r.exitCode() == null) n.putNull("exitCode"); else n.put("exitCode", r.exitCode());
        n.put("state", r.state());
        n.put("millis", r.millis());
        n.put("stdoutBytes", r.stdoutBytes());
        n.put("stderrBytes", r.stderrBytes());
        n.put("stdout", r.stdout());
        n.put("stderr", r.stderr());
        return n;
    }

    public static ArrayNode array(List<? extends ObjectNode> items) {
        var a = Wire.JSON.createArrayNode();
        items.forEach(a::add);
        return a;
    }

    static ArrayNode strings(List<String> items) {
        var a = Wire.JSON.createArrayNode();
        items.forEach(a::add);
        return a;
    }

    static ObjectNode longs(Map<String, Long> m) {
        var n = Wire.obj();
        m.forEach(n::put);
        return n;
    }

    private Json() {}
}
