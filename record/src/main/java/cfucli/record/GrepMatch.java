package cfucli.record;

import module java.base;

public record GrepMatch(long seq, long ts, String stream, String origin, String jobId,
                        long lineNumber, String line, List<String> before, List<String> after) {}
