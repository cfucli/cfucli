package cfucli.app;

import java.util.List;
import cfucli.relay.Ids;

/** What the exe was asked to be. A record rather than @Data because it carries an enum, which the
 *  DataHelper processor rejects - the same reason Frame and StreamRecord are records.
 *  <p>
 *  Flags exist for the tray and for scripts. A person double-clicking the exe passes nothing and
 *  gets the launcher, which is the only surface that has to be discoverable. {@code --local} is
 *  one of the script-only ones: it shares the shell on this machine and nowhere else, which is
 *  what an agent wants when the window is a console rather than a session with anybody. */
public record AppArgs(AppRole role, String node, String shell, String sessionId, String password, boolean local) {

    public static final String DEFAULT_NODE = "default";

    public static AppArgs parse(List<String> args) {
        var role = AppRole.ASK;
        var node = (String) null;
        var shell = (String) null;
        var sessionId = (String) null;
        var password = (String) null;
        var local = false;
        for (var i = 0; i < args.size(); i++) {
            var a = args.get(i);
            switch (a) {
                case "--tray", "tray" -> role = AppRole.TRAY;
                case "--host", "host" -> role = AppRole.HOST;
                case "--join", "join" -> {
                    role = AppRole.JOIN;
                    if (i + 1 < args.size() && !args.get(i + 1).startsWith("-")) sessionId = args.get(++i);
                }
                case "--local" -> local = true;
                case "--node" -> node = next(args, ++i);
                case "--shell" -> shell = next(args, ++i);
                case "-p", "--password" -> password = next(args, ++i);
                default -> {
                    if (role == AppRole.JOIN && sessionId == null && Ids.isSessionId(a)) sessionId = a;
                }
            }
        }
        return new AppArgs(role, node == null || node.isBlank() ? DEFAULT_NODE : node, shell, sessionId, password, local);
    }

    static String next(List<String> args, int i) {
        return i < args.size() ? args.get(i) : null;
    }

    public boolean readyToJoin() {
        return Ids.isSessionId(sessionId) && password != null && !password.isBlank();
    }
}
