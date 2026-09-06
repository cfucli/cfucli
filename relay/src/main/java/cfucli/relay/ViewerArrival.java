package cfucli.relay;

/** A viewer that presented a valid tag, with the public key that identified it.
 *  <p>
 *  The key travels with the keys it produced on purpose. Reading it back from the relay in a
 *  second call leaves a window in which a different viewer overwrites the field, and the host
 *  would then remember the wrong one as the party it just admitted. */
public record ViewerArrival(SessionKeys keys, String viewerKey) {}
