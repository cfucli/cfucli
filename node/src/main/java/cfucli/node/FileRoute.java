package cfucli.node;

import module java.base;
import cfucli.relay.SettingsStore;

/** How a file gets from one end to the other, and the reasoning made explicit.
 *  <p>
 *  Four ways, cheapest first. Two ends on one machine share a filesystem, so there is nothing to
 *  transfer and the far end simply copies. Two ends on a private wire - a LAN, or a future
 *  NAT-punched link - have real bandwidth and nothing metered, so a file of any size goes straight
 *  across in large chunks. Otherwise a small file goes through the relay as encrypted chunks sized
 *  to stay well under Upstash's own limits. A large one, with no direct link available, goes
 *  through a folder both machines already have mounted - a synced Google Drive folder is what that
 *  is for - because a hundred megabytes of base64 through a metered message bus is an abuse of the
 *  message bus and a month's command allowance.
 *  <p>
 *  Strings rather than an enum for the same reason {@link cfucli.record.Streams} is:
 *  these names go on the wire and into recordings, where outliving a code change is worth more
 *  than a compile-time check. */
public final class FileRoute {

    public static final String SAME_MACHINE = "same-machine", RELAY = "relay", SHARED = "shared",
            DIRECT = "direct", AUTO = "auto";

    public static final long DEFAULT_THRESHOLD = 256L * 1024;

    /** @param asked what the caller insisted on, or auto
     *  @param directLink true when the link the two ends are actually talking over right now is a
     *          private one - see {@link FileEnd#directLink()} - so neither the relay's size ceiling
     *          nor its small chunking applies
     *  @throws IllegalArgumentException when nothing fits, with the whole explanation as the
     *          message - it is printed to the person, so it carries both ways out. */
    public static FilePlan choose(String asked, String name, long size, long threshold,
                                  boolean sameMachine, boolean directLink, boolean senderShared, boolean receiverShared) {
        var via = asked == null || asked.isBlank() ? AUTO : asked.trim().toLowerCase();
        var limit = threshold <= 0 ? DEFAULT_THRESHOLD : threshold;
        var bothShared = senderShared && receiverShared;
        return switch (via) {
            case SAME_MACHINE -> sameMachine
                    ? plan(SAME_MACHINE, size, "both ends are this machine, so the far end copies it directly")
                    : refuse("--via same-machine, but the two ends are not on one machine");
            case DIRECT -> directLink
                    ? plan(DIRECT, size, "over the direct link because you asked for it", FileWire.CHUNK_BYTES_DIRECT)
                    : refuse("--via direct, but this session is not on a direct link right now - it is going through the relay");
            case RELAY -> plan(RELAY, size, "through the relay because you asked for it"
                                            + (size > limit ? " - " + messages(size) + ", over the "
                                                              + human(limit) + " threshold" : ""));
            case SHARED -> bothShared
                    ? plan(SHARED, size, "through the shared exchange folder because you asked for it")
                    : refuse("--via shared, but " + missing(senderShared, receiverShared));
            case AUTO -> auto(name, size, limit, sameMachine, directLink, bothShared, senderShared, receiverShared);
            default -> refuse("unknown route '" + asked + "' - one of auto, relay, shared, same-machine, direct");
        };
    }

    static FilePlan auto(String name, long size, long limit, boolean sameMachine, boolean directLink,
                         boolean bothShared, boolean senderShared, boolean receiverShared) {
        if (sameMachine) return plan(SAME_MACHINE, size, "both ends are this machine, so nothing is transferred");
        if (directLink) return plan(DIRECT, size, "over the direct link to the other machine - no relay involved, "
                                                   + "no quota spent, and no size limit", FileWire.CHUNK_BYTES_DIRECT);
        if (size <= limit) return plan(RELAY, size, "through the relay - " + messages(size));
        if (bothShared) return plan(SHARED, size, "through the shared exchange folder, being over the "
                                                  + human(limit) + " relay threshold");
        return refuse(name + " is " + human(size) + ", over the " + human(limit)
                      + " relay threshold, and " + missing(senderShared, receiverShared) + "."
                      + System.lineSeparator() + "Two ways on:"
                      + System.lineSeparator() + "  - set largeFileExchangeDir in " + SettingsStore.path()
                      + " on BOTH machines, to a folder they both have mounted; a Google Drive folder they"
                      + " already sync is exactly what this is for"
                      + System.lineSeparator() + "  - pass --via relay to push it through the relay anyway: "
                      + messages(size));
    }

    static String missing(boolean senderShared, boolean receiverShared) {
        if (!senderShared && !receiverShared) return "neither end has a shared exchange folder configured";
        return (senderShared ? "the far end" : "this end") + " has no shared exchange folder configured";
    }

    static FilePlan plan(String route, long size, String why) {
        return plan(route, size, why, FileWire.CHUNK_BYTES_DEFAULT);
    }

    static FilePlan plan(String route, long size, String why, int chunkBytes) {
        var chunked = RELAY.equals(route) || DIRECT.equals(route);
        var chunks = chunked ? (int) Math.max(1, (size + chunkBytes - 1) / chunkBytes) : 0;
        return new FilePlan().route(route).why(why).size(size).chunks(chunks).chunkBytes(chunkBytes);
    }

    static FilePlan refuse(String why) {
        throw new IllegalArgumentException(why);
    }

    static String messages(long size) {
        var n = Math.max(1, (size + FileWire.CHUNK_BYTES_DEFAULT - 1) / FileWire.CHUNK_BYTES_DEFAULT);
        return n == 1 ? "one message" : n + " messages of " + human(FileWire.CHUNK_BYTES_DEFAULT);
    }

    public static String human(long n) {
        if (n < 1024) return n + "B";
        if (n < 1024 * 1024) return Math.round(n / 1024.0) + "K";
        return String.format(Locale.ROOT, "%.1fM", n / (1024.0 * 1024));
    }

    private FileRoute() {}
}
