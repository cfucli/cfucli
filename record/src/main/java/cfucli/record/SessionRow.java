package cfucli.record;

import datapotter.arcadedbhelper.ArcadeData;
import datapotter.arcadedbhelper.TypeDef;

@ArcadeData
public final class SessionRow extends SessionRow_A {

    String sessionId;
    String role;
    String hostName;
    String shell;
    String transport;
    Long startedAt;
    Long endedAt;
    String endReason;

    public static final TypeDef<SessionRow> TYPEDEF =
            schemaBuilder()
                    .factory(SessionRow::new)
                    .unique($sessionId)
                    .lsmIndex($startedAt)
                    .__();
}
