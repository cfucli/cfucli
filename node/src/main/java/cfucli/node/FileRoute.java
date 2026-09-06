package cfucli.node;

import module java.base;
import cfucli.relay.SettingsStore;

/** How a file gets from one end to the other, and the reasoning made explicit.
 *  <p>
 *  Three ways, cheapest first. Two ends on one machine share a filesystem, so there is nothing to
 *  transfer and the far end simply copies. A small file goes through the relay as encrypted chunks.
 *  A large one goes through a folder both machines already have mounted - a synced Google Drive
 *  folder is what that is for - because a hundred megabytes of base64 through a metered message bus
 *  is an abuse of the message bus and a month's command allowance.
 *  <p>
 *  Strings rather than an enum for the same reason {@link cfucli.record.Streams} is:
 *  these names go on the wire and into recordings, where outliving a code change is worth more
 *  than a compile-time check. */
public final class FileRoute {

    public static final String SAME_MACHINE = "same-machine", RELAY = "relay", SHARED = "shared", AUTO = "auto";

    public static final long DEFAULT_THRESHOLD = 256L * 1024;

    /** @param asked what the caller insisted on, or auto
     *  @throws IllegalArgumentException when nothing fits, with the whole explanation as the
     *          message - it is printed to the person, so it carries both ways out. */
    public static FilePlan choose(String asked, String name, long size, long threshold,
                                  boolean sameMachine, boolean senderShared, boolean receiverShared) {
        var via = asked == null || asked.isBlank() ? AUTO : asked.trim().toLowerCase();
        var limit = threshold <= 0 ? DEFAULT_THRESHOLD : threshold;
        var bothShared = senderShared && receiverShared;
        return switch (via) {
            case SAME_MACHINE -> sameMachine
                    ? plan(SAME_MACHINE, size, "both ends are this machine, so the far end copies it directly")
                    : refuse("--via same-machine, but the two ends are not on one machine");
            case RELAY -> plan(RELAY, size, "through the relay because you asked for it"
                                            + (size > limit ? " - " + messages(size) + ", over the "
                                                              + human(limit) + " threshold" : ""));
            case SHARED -> bothShared
                    ? plan(SHARED, size, "through the shared exchange folder because you asked for it")
                    : refuse("--via shared, but " + missing(senderShared, receiverShared));
            case AUTO -> auto(name, size, limit, sameMachine, bothShared, senderShared, receiverShared);
            default -> refuse("unknown route '" + asked + "' - one of auto, relay, shared, same-machine");
        };
    }

    static FilePlan auto(String name, long size, long limit, boolean sameMachine, boolean bothShared,
                         boolean senderShared, boolean receiverShared) {
        if (sameMachine) return plan(SAME_MACHINE, size, "both ends are this machine, so nothing is transferred");
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
        var chunk = FileWire.CHUNK_BYTES_DEFAULT;
        var chunks = RELAY.equals(route) ? (int) Math.max(1, (size + chunk - 1) / chunk) : 0;
        return new FilePlan().route(route).why(why).size(size).chunks(chunks).chunkBytes(chunk);
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
