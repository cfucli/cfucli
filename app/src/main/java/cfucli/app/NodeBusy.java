package cfucli.app;

import cfucli.node.NodeInfo;

/** Thrown when the node name the window wants is already held by another process.
 *  <p>
 *  Not recoverable by picking a name silently: ArcadeDB takes an exclusive lock on the store
 *  directory, so a second holder of the same name is not a slow path but an impossible one, and
 *  the process that already has it may be running a live session for someone. */
public final class NodeBusy extends RuntimeException {

    public final NodeInfo held;

    public NodeBusy(NodeInfo held) {
        super("a node called '" + held.node() + "' is already running on port " + held.port()
              + " (pid " + held.pid() + ")");
        this.held = held;
    }
}
