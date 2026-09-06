package cfucli.app;

import xyz.jphil.datahelper.Data;

/** One row of the manager: a node running on this machine and what it is doing.
 *  <p>
 *  {@code role} distinguishes the four states that must never be collapsed into an empty list -
 *  hosting, viewing, running but idle, and running but not answering. A manager that shows a
 *  silent node as absent is the failure this whole tool was built to stop happening. */
@Data
public final class NodeCard extends NodeCard_A {
    String node;
    Integer port;
    Long pid;
    Boolean hasWindow;
    Boolean windowVisible;
    String role;
    String sessionId;
    String prettyId;
    Boolean connected;
    Boolean locked;
    Boolean viewOnly;
    String shell;
    String transport;
    String detail;
}
