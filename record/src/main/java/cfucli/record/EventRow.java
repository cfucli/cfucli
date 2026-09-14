package cfucli.record;

import datapotter.arcadedbhelper.ArcadeData;
import datapotter.arcadedbhelper.TypeDef;

/** One recorded moment in a session. {@code text} is exactly what crossed the wire, escape codes
 *  and all, because a replay needs them; {@code plain} is the same thing with the escapes taken
 *  out, because that is what a search should match. Keeping both means grep never has to choose
 *  between finding the word and reproducing the screen. */
@ArcadeData
public final class EventRow extends EventRow_A {

    String eventId;
    String sessionId;
    Long seq;
    Long ts;
    String stream;
    String origin;
    String jobId;
    String text;
    String plain;
    Integer bytes;
    Boolean redacted;

    public static final TypeDef<EventRow> TYPEDEF =
            schemaBuilder()
                    .factory(EventRow::new)
                    .unique($eventId)
                    .lsmIndex($sessionId)
                    .lsmIndex($seq)
                    .lsmIndex($ts)
                    .lsmIndex($jobId)
                    .lsmIndex($stream)
                    .__();

    public static String id(String sessionId, long seq) {
        return sessionId + "#" + seq;
    }
}
