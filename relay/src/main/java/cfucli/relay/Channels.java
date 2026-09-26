package cfucli.relay;

/** Every key this tool writes starts with "ucli:", so the database it shares can be told apart
 *  from anything else at a glance and cleaned by prefix. */
public final class Channels {

    public static final String NS = "ucli";

    public static String meta(String sessionId) {
        return NS + ":s:" + sessionId;
    }

    public static String stream(String sessionId, Direction d) {
        return NS + ":x:" + sessionId + ":" + d.wire;
    }

    public static String notify(String sessionId, Direction d) {
        return NS + ":n:" + sessionId + ":" + d.wire;
    }

    public static String directory() {
        return NS + ":live";
    }

    /** The shared "who's around" roster - see {@link Presence}. */
    public static String presence() {
        return NS + ":presence";
    }

    /** Requests aimed at one identity, waiting to be noticed. */
    public static String request(String targetIdentity) {
        return NS + ":req:" + targetIdentity;
    }

    /** One request's answer - unique per request, so it needs no packing and no staleness filter
     *  beyond its own TTL. */
    public static String reply(String requesterIdentity, String requestId) {
        return NS + ":reply:" + requesterIdentity + ":" + requestId;
    }

    private Channels() {}
}
