package cfucli.relay;

/** The two halves of an invite, once they have been told apart. A record because it is a value
 *  compared whole and never mutated - {@link Invite#parse} either produces one or produces
 *  nothing. */
public record Invited(String sessionId, String password) {

    public String formatted() {
        return Invite.format(sessionId, password);
    }
}
