package cfucli.node;

import com.fasterxml.jackson.databind.JsonNode;
import cfucli.node.PtyHost;

/** What the host last said about itself. Compared whole and never mutated, which is the case a
 *  record is for.
 *  <p>
 *  It exists so the viewer can say something true in its banner. Reading the same facts off the
 *  relay's meta hash would work but lags a heartbeat behind, and the two that matter most - the
 *  lock going on and the geometry changing - are exactly the ones the person watching needs to
 *  see the moment they happen. */
public record RemoteState(int columns, int rows, boolean locked, boolean viewOnly,
                          String hostName, String shell, boolean sharedExchange, boolean known) {

    public static RemoteState unknown() {
        return new RemoteState(PtyHost.DEFAULT_COLUMNS, PtyHost.DEFAULT_ROWS, false, false, null, null, false, false);
    }

    public static RemoteState of(JsonNode n) {
        return new RemoteState(
                Wire.i(n, "columns", PtyHost.DEFAULT_COLUMNS),
                Wire.i(n, "rows", PtyHost.DEFAULT_ROWS),
                Wire.bool(n, "locked", false),
                Wire.bool(n, "viewOnly", false),
                Wire.str(n, "hostName", null),
                Wire.str(n, "shell", null),
                Wire.bool(n, "sharedExchange", false),
                true);
    }

    public boolean restricted() {
        return locked || viewOnly;
    }
}
