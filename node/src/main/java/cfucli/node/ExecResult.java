package cfucli.node;

public record ExecResult(String jobId, Integer exitCode, String state, long stdoutBytes, long stderrBytes,
                         long millis, String stdout, String stderr) {

    public static final String OK = "ok", TIMEOUT = "timeout", FAILED = "failed", CANCELLED = "cancelled";

    public boolean succeeded() {
        return OK.equals(state) && exitCode != null && exitCode == 0;
    }
}
