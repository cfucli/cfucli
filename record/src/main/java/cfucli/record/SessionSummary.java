package cfucli.record;

import module java.base;

/** The zoomed-out view: enough to decide where to look without reading any output. */
public record SessionSummary(String sessionId, String role, String hostName, String shell, String transport,
                             Long startedAt, Long endedAt, String endReason,
                             long events, long jobs, long failedJobs, long redactedEvents,
                             Map<String, Long> bytesByStream, Map<String, Long> eventsByStream) {

    public Long durationMillis() {
        return startedAt == null ? null : (endedAt == null ? System.currentTimeMillis() : endedAt) - startedAt;
    }

    public long totalBytes() {
        return bytesByStream.values().stream().mapToLong(Long::longValue).sum();
    }

    public boolean live() {
        return endedAt == null;
    }
}
