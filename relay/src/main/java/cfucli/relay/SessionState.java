package cfucli.relay;

/** Never collapse these into an empty result. Telling "there is no such session" apart from
 *  "the session exists and the host has stopped answering" is the difference between a tool that
 *  is debuggable and one that looks healthy while doing nothing. */
public enum SessionState {
    NO_SESSION, HOST_SILENT, HOST_RESPONDING, ENDED;

    public boolean usable() {
        return this == HOST_RESPONDING;
    }
}
