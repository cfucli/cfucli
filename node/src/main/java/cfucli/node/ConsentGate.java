package cfucli.node;

/** Asked once per viewer arrival, on the host. The headless implementation treats starting the
 *  host and reading the one-time password to someone as the consent - which it is, since a fresh
 *  password is generated per session and nobody joins without it. The window supplies a real
 *  prompt on top of that.
 *  <p>
 *  There is deliberately no always-accept mode: a channel that opens itself is a backdoor whoever
 *  wrote it. The interface exists so that decision stays in one place if it is ever revisited. */
public interface ConsentGate {

    boolean allow(String sessionId, String detail);

    ConsentGate PASSWORD_IS_CONSENT = (sessionId, detail) -> true;

    ConsentGate DENY = (sessionId, detail) -> false;
}
