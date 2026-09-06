package cfucli.cli;

import module java.base;
import cfucli.relay.Invite;
import picocli.CommandLine.*;

@Command(name = "join", description = "Connect to a shared session, using the invite string or the id and password.")
public final class JoinCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "the invite string, or the session id if -p is given")
    String sessionId;

    /** No longer required: an invite carries the password, and demanding the flag anyway would
     *  mean the string a person was told to paste does not work when pasted. */
    @Option(names = {"-p", "--password"}, description = "the one-time password, when it was given separately")
    String password;

    @Override
    public Integer call() {
        var id = sessionId;
        var pw = password;
        if (pw == null || pw.isBlank()) {
            var invited = Invite.parse(sessionId);
            if (invited.isEmpty()) {
                Out.err("cfucli: that is not an invite. Paste the whole string the other end copied,"
                        + " or give the id and -p <password>.");
                return Exits.USAGE;
            }
            id = invited.get().sessionId();
            pw = invited.get().password();
        }
        var r = opts.client().call("join", Map.of("sessionId", id, "password", pw), Duration.ofMinutes(2));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        var status = r.path("status");
        Out.line("Joined " + r.path("sessionId").asText() + " - " + status.path("detail").asText());
        if (status.hasNonNull("hostName")) {
            Out.line("Host " + status.path("hostName").asText() + " running " + status.path("shell").asText("a shell"));
        }
        return status.path("usable").asBoolean(false) ? 0 : 4;
    }
}
