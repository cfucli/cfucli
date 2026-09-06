package cfucli.cli;

import module java.base;
import cfucli.node.NodeException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** The agent's surface. Run with nothing, or with --help, it prints the manual rather than a list
 *  of flags: a flag list tells you what may be typed and nothing about what the tool is, and the
 *  first thing anybody arriving here needs is the second. Each verb keeps its own precise --help. */
@Command(name = "cfucli", version = "cfucli 0.1",
        description = "One real shell, watched by a human, driven from here - on this machine or across the internet.",
        subcommands = {ConsoleCmd.class, HostCmd.class, JoinCmd.class, LocalCmd.class, StatusCmd.class, SessionsCmd.class,
                ExecCmd.class, WaitCmd.class, CancelCmd.class, SendKeysCmd.class, ScreenCmd.class,
                PutCmd.class, GetCmd.class, RunScriptCmd.class,
                TailCmd.class, EventsCmd.class, GrepCmd.class, JobsCmd.class, JobCmd.class, SummaryCmd.class,
                EndCmd.class, LockCmd.class, ViewOnlyCmd.class,
                ForgetCmd.class, ScrubCmd.class, RetainCmd.class, NodeCmd.class, RelayCmd.class, GuideCmd.class})
public final class CfuCli implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, description = "print the manual")
    boolean help;

    @Option(names = {"-V", "--version"}, versionHelp = true, description = "print the version")
    boolean version;

    @Override
    public Integer call() {
        System.out.print(GuideCmd.text());
        return 0;
    }

    public static void main(String[] args) {
        utf8Console();
        var cmd = new CommandLine(new CfuCli())
                .setExecutionExceptionHandler((e, c, parse) -> {
                    Out.err("cfucli: " + reason(e));
                    return e instanceof NodeException ? 4 : 70;
                });
        System.exit(cmd.execute(args));
    }

    /** Windows consoles still default to a legacy code page, and without this every Devanagari
     *  or box-drawing character in a transcript comes back as mojibake. */
    static void utf8Console() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    static String reason(Throwable e) {
        var root = e;
        while (root.getCause() != null && root.getMessage() == null) root = root.getCause();
        var m = root.getMessage();
        return m == null || m.isBlank() ? root.getClass().getSimpleName() : m;
    }
}
