package cfucli.node;

import cfucli.record.Recorder;
import cfucli.relay.FrameType;
import cfucli.relay.Settings;

/** What a file transfer needs from whichever end of the session it is running on.
 *  <p>
 *  One {@link FileMover} serves both ends, because the halves are symmetric: a put and a get are
 *  the same machinery with the sender and receiver swapped. This is the small surface that differs. */
public interface FileEnd {

    void send(FrameType type, byte[] payload);

    Recorder recorder();

    /** Said on the activity channel and in the recording, and deliberately not in the terminal:
     *  a progress line spliced into a program's output belongs to neither. */
    void note(String text);

    Settings settings();

    /** True when the two ends are one computer, in which case there is nothing to transfer and the
     *  far end can simply copy from the path it is given. */
    boolean sameMachine();

    /** True when the far end has been refused and told why, so the caller must not proceed. */
    boolean refuse(String what);

    /** Whether the far end has a shared exchange folder. Meaningful on a viewer, which learns it
     *  from the host's STATE frame; a host does not consult it, because a get request carries the
     *  viewer's own answer instead of relying on a stale one. */
    boolean peerShared();

    /** Whether the two ends are actually joined, rather than merely both present.
     *  <p>
     *  A viewer can hold an open session that the host has not admitted yet - the host is showing a
     *  consent prompt and nobody has answered it. Without this, a transfer issued in that window
     *  simply blocks until its timeout runs out, which is the "looked healthy and silently did
     *  nothing" failure this tool was built to avoid. */
    boolean attached();
}
