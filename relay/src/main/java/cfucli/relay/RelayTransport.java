package cfucli.relay;

import module java.base;

/** What the rest of the tool is allowed to know about Upstash: an append-only log per direction,
 *  a small metadata hash per session, and nothing else. Two implementations sit behind it - the
 *  native Redis protocol on 6379, and HTTPS on 443 for networks that will not pass 6379.
 *  <p>
 *  Payloads are base64 on the wire in BOTH implementations. That costs a third of the bandwidth
 *  and buys the thing that matters: a host on one transport and a viewer on the other read
 *  byte-identical entries. */
public interface RelayTransport extends AutoCloseable {

    String name();

    /** True when nothing this transport does crosses a network or costs a metered command.
     *  Everything above the seam that has to choose between latency and thrift reads this. */
    default boolean local() {
        return false;
    }

    /** How long output may be gathered before it is sent. The relay path coalesces because a
     *  message per keystroke would spend a month's free command allowance in a couple of busy
     *  days; the local path has no allowance to protect and sends as it comes. */
    default Duration outputWindow() {
        return Duration.ofMillis(60);
    }

    default int outputMaxBytes() {
        return 16 * 1024;
    }

    /** How often it is worth asking whether the session hash has changed. Each of these is a
     *  billable command on the relay and free on the loopback, so the two ends of that trade
     *  belong to the transport rather than to the caller. */
    default Duration pollInterval() {
        return Duration.ofSeconds(2);
    }

    String append(String stream, byte[] payload, long maxLen);

    List<StreamRecord> read(String stream, String fromId, int count, Duration block);

    /** The id of the newest entry, or {@link #FIRST_ID} when the stream is empty.
     *  <p>
     *  Callers resolve {@link #LAST_ID} through this before following a stream. The bare "$" only
     *  means "whatever arrives next" to a BLOCKING read; a polling reader that keeps asking from
     *  "$" is told about nothing, for ever, and looks perfectly healthy doing it. */
    String lastId(String stream);

    void putMeta(String key, Map<String, String> fields, Duration ttl);

    Map<String, String> getMeta(String key);

    void touch(String key, Duration ttl);

    void delete(String... keys);

    /** Throws with the real reason if the far end is not answering. */
    void ping();

    default boolean alive() {
        try {
            ping();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    void close();

    String FIRST_ID = "0-0", LAST_ID = "$";
}
