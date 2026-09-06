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

    private Channels() {}
}
