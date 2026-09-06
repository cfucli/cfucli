package cfucli.cli;

/** The exit codes this cli promises, in code rather than only in the manual.
 *  <p>
 *  They were documented before they were named here, and exec drifted off them: it answered
 *  {@link #FAILED} when the far end refused it, where every other verb answered {@link #REFUSED}.
 *  The difference is not cosmetic to the thing reading it. REFUSED means the far end understood
 *  and said no, so asking again the same way is pointless until something changes. FAILED means
 *  nobody knows what happened, which is worth a retry. Collapsing the two is the same fault as
 *  reporting "no session" and "host not answering" as one empty result.
 *  <p>
 *  exec and wait are the exception to all of this: they return the command's own exit code, so
 *  that running something through this cli behaves in a script exactly as running it locally
 *  would. These codes apply when there is no command exit code to give back. */
public final class Exits {

    /** Did what was asked. */
    public static final int OK = 0;

    /** The arguments were wrong, and nothing was sent anywhere. Picocli uses 2 for this, so a
     *  verb that validates its own input before touching the node agrees with the parser. */
    public static final int USAGE = 2;

    /** The far end or the node refused it; the reason is on stderr. */
    public static final int REFUSED = 4;

    /** Nothing to talk to - no node running, or no screen to show. */
    public static final int NOTHING = 5;

    /** Something else went wrong, or an exit code could not be determined. */
    public static final int FAILED = 70;

    private Exits() {}
}
