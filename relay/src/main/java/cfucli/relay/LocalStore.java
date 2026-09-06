package cfucli.relay;

import module java.base;

/** The relay, when the relay is this machine.
 *  <p>
 *  Two ends of a session on one computer have no business paying a round trip to a datacentre for
 *  every keystroke, and on a metered free tier they cannot afford to. So a local host publishes
 *  one of these instead: append-only streams, a small hash per session, and a genuinely blocking
 *  read - the same three things Upstash provides, minus the network and minus the bill.
 *  <p>
 *  Ids are a per-stream counter rather than a timestamp. They only ever have to be ordered and
 *  comparable to the previous one, and a counter cannot collide the way a millisecond clock can. */
public final class LocalStore {

    final Map<String, List<StreamRecord>> streams = new ConcurrentHashMap<>();
    final Map<String, Long> counters = new ConcurrentHashMap<>();
    final Map<String, Map<String, String>> meta = new ConcurrentHashMap<>();

    public String append(String stream, byte[] payload, long maxLen) {
        var list = entries(stream);
        synchronized (list) {
            var id = counters.merge(stream, 1L, Long::sum) + "-0";
            list.add(new StreamRecord(id, payload == null ? new byte[0] : payload));
            if (maxLen > 0) while (list.size() > maxLen) list.removeFirst();
            list.notifyAll();
            return id;
        }
    }

    public List<StreamRecord> read(String stream, String fromId, int count, Duration block) {
        var list = entries(stream);
        var deadline = System.nanoTime() + (block == null ? 0 : block.toNanos());
        synchronized (list) {
            var after = RelayTransport.LAST_ID.equals(fromId) ? lastSeq(list) : seqOf(fromId);
            for (;;) {
                var out = since(list, after, count <= 0 ? Integer.MAX_VALUE : count);
                if (!out.isEmpty()) return out;
                var leftNanos = deadline - System.nanoTime();
                if (leftNanos <= 0) return List.of();
                try {
                    list.wait(Math.max(1, leftNanos / 1_000_000), (int) (leftNanos % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return List.of();
                }
            }
        }
    }

    public String lastId(String stream) {
        var list = entries(stream);
        synchronized (list) {
            return list.isEmpty() ? RelayTransport.FIRST_ID : list.getLast().id();
        }
    }

    public void putMeta(String key, Map<String, String> fields, Duration ttl) {
        meta.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).putAll(fields);
    }

    public Map<String, String> getMeta(String key) {
        var m = meta.get(key);
        return m == null ? Map.of() : Map.copyOf(m);
    }

    public void delete(String... keys) {
        for (var k : keys) {
            meta.remove(k);
            var list = streams.remove(k);
            counters.remove(k);
            if (list != null) synchronized (list) {
                list.notifyAll();
            }
        }
    }

    List<StreamRecord> entries(String stream) {
        return streams.computeIfAbsent(stream, k -> new ArrayList<>());
    }

    /** Ids are contiguous within a stream and trimming only ever drops from the front, so where a
     *  cursor lands is arithmetic rather than a scan. It has to be: a per-character local session
     *  reads once per frame, and a scan from the head each time would be quadratic in the length
     *  of the session. */
    static List<StreamRecord> since(List<StreamRecord> list, long after, int count) {
        if (list.isEmpty()) return List.of();
        var start = (int) Math.max(0, Math.min(list.size(), after - seqOf(list.getFirst().id()) + 1));
        if (start >= list.size()) return List.of();
        var end = count >= list.size() - start ? list.size() : start + count;
        return List.copyOf(list.subList(start, end));
    }

    static long lastSeq(List<StreamRecord> list) {
        return list.isEmpty() ? 0 : seqOf(list.getLast().id());
    }

    static long seqOf(String id) {
        if (id == null || id.isBlank()) return 0;
        var dash = id.indexOf('-');
        try {
            return Long.parseLong(dash < 0 ? id : id.substring(0, dash));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
