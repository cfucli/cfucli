package cfucli.node;

import xyz.jphil.datahelper.Data;

/** Which of the three ways a file is going to travel, how many messages that costs, and the one
 *  sentence a person should be told about it. The sentence is part of the plan rather than a log
 *  line: a transfer that quietly took the expensive route is the failure this is here to prevent. */
@Data
public final class FilePlan extends FilePlan_A {

    String route;
    String why;
    Long size = 0L;
    Integer chunks = 0;
    Integer chunkBytes = FileWire.CHUNK_BYTES_DEFAULT;

    public boolean relay() {
        return FileRoute.RELAY.equals(route);
    }

    public boolean shared() {
        return FileRoute.SHARED.equals(route);
    }

    public boolean sameMachine() {
        return FileRoute.SAME_MACHINE.equals(route);
    }
}
