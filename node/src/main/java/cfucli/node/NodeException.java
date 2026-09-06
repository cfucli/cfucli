package cfucli.node;

/** The node refused a request and said why. Carried separately so the cli can print the reason
 *  without a stack trace, which is what an agent reading stderr actually needs. */
public final class NodeException extends RuntimeException {

    public NodeException(String message) {
        super(message);
    }
}
