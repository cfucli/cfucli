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
     *  Everything above the seam that has to choose between latency and thrift reads this.
     *  <p>
     *  Specifically the loopback: both ends are one computer, so a file transfer has a filesystem
     *  to cross rather than a wire. A LAN or NAT-punched peer is a different machine and must not
     *  answer this true, however fast and free it also is - see {@link #direct()} for that. */
    default boolean local() {
        return false;
    }

    /** True when this transport is a private pipe straight to the other end - the loopback, a LAN
     *  connection, or a NAT-punched one - as opposed to Upstash, which is shared, metered, and
     *  capped in message size. A file transfer reads this to decide whether the relay's chunk
     *  threshold and size ceiling apply at all; nothing about this implies the two ends share a
     *  filesystem, which is what {@link #local()} is for. Defaults to {@link #local()} because
     *  every local transport is also direct. */
    default boolean direct() {
        return local();
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

    /** Drops every entry older than {@code minId} - the reader calls this with what it has already
     *  consumed, so a stream on the relay holds only what is in flight rather than a whole file.
     *  Storage on the relay is capped for the whole database; on the loopback it is not, and the
     *  local store trims by length anyway, so the default does nothing. */
    default void trim(String stream, String minId) {}

    /** Keys matching a glob, for the sweep that gives forgotten keys a TTL. Empty where there is no
     *  shared database to sweep. */
    default List<String> keys(String pattern) {
        return List.of();
    }

    /** Seconds left, -1 for no expiry, -2 for no such key - Redis's own answer. */
    default long ttl(String key) {
        return -2;
    }

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
