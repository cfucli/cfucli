package cfucli.cli;

import module java.base;
import cfucli.relay.RelayBlob;
import cfucli.relay.Settings;
import cfucli.relay.SettingsStore;
import picocli.CommandLine.*;

/** Which rendezvous this machine uses, and how to point it at a different one.
 *  <p>
 *  This exists so that rotating a credential is a thing you can ask a person to do. A distributed
 *  copy is shipped with the credential already in its settings.toml, which means every machine that
 *  copy reached is on the same relay account for as long as that account exists - and the only way
 *  to move one of them used to be to talk whoever is at the keyboard through editing three lines of
 *  TOML. That is not a rotation plan. It is also where a Windows path in double quotes turns every
 *  backslash into an escape and the node then refuses to start at all.
 *  <p>
 *  So: one command, given a file. "Rotate the credential" is only a real answer if the doing of it
 *  is one step. */
@Command(name = "relay", description = "Show or change the rendezvous this machine uses.",
        subcommands = {RelayShowCmd.class, RelaySetCmd.class, RelayClearCmd.class})
public final class RelayCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Override
    public Integer call() {
        return new RelayShowCmd().call();
    }
}

@Command(name = "show", description = "What rendezvous is configured, with the secrets masked.")
final class RelayShowCmd implements Callable<Integer> {

    @Mixin CommonOpts opts = new CommonOpts();

    @Option(names = "--reveal", description = "print the real values instead of a mask")
    boolean reveal;

    @Override
    public Integer call() {
        var s = SettingsStore.load();
        Out.line("settings   " + SettingsStore.path());
        Out.line("REDIS_URL                 " + shown(s.redisUrl(), reveal));
        Out.line("UPSTASH_REDIS_REST_URL    " + shown(s.restUrl(), reveal));
        Out.line("UPSTASH_REDIS_REST_TOKEN  " + shown(s.restToken(), reveal));
        Out.line("");
        Out.line(configured(s)
                ? "This machine can host and join sessions across the internet."
                : "No rendezvous configured, so this machine is local-only: a console here works and"
                  + " nothing is announced anywhere. Point it at one with: cfucli relay set --from <file>");
        return 0;
    }

    /** Masked by default and length-bearing, because "is something there" and "is it the right
     *  thing" are different questions and only the first one is usually being asked. */
    static String shown(String v, boolean reveal) {
        if (v == null || v.isBlank()) return "(not set)";
        return reveal ? v : "<set, " + v.length() + " chars>";
    }

    static boolean configured(Settings s) {
        return notBlank(s.redisUrl()) || (notBlank(s.restUrl()) && notBlank(s.restToken()));
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

@Command(name = "set", description = "Point this machine at a rendezvous, reading the values from a file.")
final class RelaySetCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    /** A file or stdin, and deliberately NO option that takes a token as its argument. A secret on
     *  a command line is in the shell history and in any agent transcript of that shell, for good,
     *  where nobody will ever think to look for it. Reading it from a file keeps it out of both. */
    @Option(names = "--from", required = true,
            description = "file holding the REDIS_URL and UPSTASH_REDIS_REST_* lines; - reads stdin")
    String from;

    @Override
    public Integer call() throws Exception {
        var text = "-".equals(from)
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                : readFile();
        if (text == null) return Exits.USAGE;
        var found = RelayBlob.parse(text);
        if (found.isEmpty()) {
            Out.err("cfucli: no REDIS_URL or UPSTASH_REDIS_REST_* line in that input."
                    + " Paste the three lines from the Upstash console, or from a settings.toml.");
            return Exits.USAGE;
        }
        SettingsStore.update(s -> found.forEach((k, v) -> RelayBlob.apply(s, k, v)));
        Out.line("updated " + SettingsStore.path());
        found.keySet().forEach(k -> Out.line("  set " + k));
        Out.line("");
        Out.line("A node already running still holds the old settings - restart it: cfucli node stop");
        return 0;
    }

    String readFile() {
        try {
            return Files.readString(Paths.get(from), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Out.err("cfucli: cannot read " + from + " - " + e.getMessage());
            return null;
        }
    }
}

@Command(name = "clear", description = "Forget the rendezvous, making this machine local-only.")
final class RelayClearCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--yes", description = "actually do it; without this the command only says what it would do")
    boolean yes;

    @Override
    public Integer call() {
        var s = SettingsStore.load();
        if (!RelayShowCmd.configured(s)) {
            Out.line("no rendezvous is configured; nothing to clear");
            return 0;
        }
        if (!yes) {
            Out.line("would remove the credentials from " + SettingsStore.path() + ", leaving this");
            Out.line("machine local-only. Nothing has been changed. Add --yes to do it.");
            return 0;
        }
        SettingsStore.update(x -> {
            x.redisUrl(null);
            x.restUrl(null);
            x.restToken(null);
        });
        Out.line("credentials removed from " + SettingsStore.path());
        Out.line("This machine is now local-only. A running node still holds the old ones:"
                 + " cfucli node stop");
        return 0;
    }
}
