package cfucli.record;

import module java.base;
import com.arcadedb.database.Database;

/** Regex over a session, on a flattened line view rather than per stored event - a shell writes a
 *  line in as many pieces as it feels like, so matching inside one event would miss anything that
 *  straddled a chunk boundary. Context lines cross event boundaries for the same reason. */
public final class SessionGrep {

    public static List<GrepMatch> grep(Database db, String sessionId, String regex, List<String> streams,
                                       int limit, int context, boolean ignoreCase) {
        var pattern = Pattern.compile(regex, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
        var rows = SessionQueries.scanEvents(db, sessionId, null, null, streams);

        var out = new ArrayList<GrepMatch>();
        var before = new ArrayDeque<String>();
        var pending = new ArrayList<GrepMatch>();
        var lineNo = 0L;
        var carry = "";
        var cap = limit <= 0 ? SessionQueries.DEFAULT_LIMIT : limit;

        var prev = (EventRow) null;
        for (var e : rows) {
            // A note begins its own line. A shell leaves its prompt unterminated by design, so
            // without this the next note is glued to the end of it and reads as though the shell
            // printed it - "C:\Users\User>exec j1: dir" - which is the exact confusion keeping the
            // tool's voice off the terminal exists to prevent.
            var gap = Streams.isNote(e.stream()) && !carry.isEmpty() ? "\n" : "";
            var text = carry + gap + (e.plain() == null ? "" : e.plain());
            var parts = text.split("\r?\n", -1);
            carry = parts[parts.length - 1];
            for (var i = 0; i < parts.length - 1; i++) {
                lineNo++;
                var line = parts[i];
                // The line the gap closed off was written by whoever wrote it, not by the note
                // that forced the break - tagging a prompt as [control] is the same mislabelling
                // in miniature.
                var src = !gap.isEmpty() && i == 0 && prev != null ? prev : e;
                fillAfter(pending, line, context, out);
                if (pattern.matcher(line).find() && out.size() + pending.size() < cap) {
                    pending.add(new GrepMatch(src.seq(), src.ts(), src.stream(), src.origin(), src.jobId(),
                            lineNo, line, List.copyOf(before), new ArrayList<>()));
                }
                before.addLast(line);
                while (before.size() > context) before.removeFirst();
            }
            prev = e;
            if (out.size() >= cap) break;
        }
        if (!carry.isEmpty()) {
            lineNo++;
            fillAfter(pending, carry, context, out);
            if (pattern.matcher(carry).find() && out.size() + pending.size() < cap) {
                pending.add(new GrepMatch(-1, -1, "", "", null, lineNo, carry, List.copyOf(before), new ArrayList<>()));
            }
        }
        out.addAll(pending);
        out.sort(Comparator.comparingLong(GrepMatch::lineNumber));
        return out.size() > cap ? out.subList(0, cap) : out;
    }

    static void fillAfter(List<GrepMatch> pending, String line, int context, List<GrepMatch> done) {
        if (pending.isEmpty()) return;
        var it = pending.iterator();
        while (it.hasNext()) {
            var m = it.next();
            if (m.after().size() < context) {
                m.after().add(line);
            }
            if (m.after().size() >= context) {
                done.add(m);
                it.remove();
            }
        }
    }

    private SessionGrep() {}
}
