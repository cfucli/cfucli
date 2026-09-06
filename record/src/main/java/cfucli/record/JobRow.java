package cfucli.record;

import xyz.jphil.arcadedb.datahelper.ArcadeData;
import xyz.jphil.arcadedb.datahelper.TypeDef;

/** One command run through {@code exec}. This is the high-level index: what ran, who asked for it,
 *  how long it took, how much it produced and how it ended - enough to decide where to look
 *  without reading a byte of the output itself. */
@ArcadeData
public final class JobRow extends JobRow_A {

    String jobId;
    String sessionId;
    String command;
    String workingDir;
    String origin;
    Long startedAt;
    Long endedAt;
    Integer exitCode;
    Long stdoutBytes;
    Long stderrBytes;
    String state;

    public static final TypeDef<JobRow> TYPEDEF =
            schemaBuilder()
                    .factory(JobRow::new)
                    .unique($jobId)
                    .lsmIndex($sessionId)
                    .lsmIndex($startedAt)
                    .lsmIndex($exitCode)
                    .__();

    public Long durationMillis() {
        return startedAt == null || endedAt == null ? null : endedAt - startedAt;
    }
}
