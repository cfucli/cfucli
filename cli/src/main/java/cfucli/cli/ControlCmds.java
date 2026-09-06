package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

@Command(name = "end", description = "End the session on this node.")
final class EndCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Override
    public Integer call() {
        var r = opts.existing().call("end", Map.of());
        if (opts.json) Out.json(r); else Out.line("session ended");
        return 0;
    }
}

@Command(name = "cancel", description = "Kill a job that is still running.")
final class CancelCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "the job id")
    String jobId;

    @Override
    public Integer call() {
        var r = opts.existing().call("cancel", Map.of("jobId", jobId));
        if (opts.json) Out.json(r);
        else Out.line(r.path("cancelled").asBoolean() ? "cancelled " + jobId
                : jobId + " was not running here - it may have finished already");
        return r.path("cancelled").asBoolean() ? 0 : 4;
    }
}

@Command(name = "lock", description = "Stop the remote end doing anything, without ending the session.")
final class LockCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--off", description = "unlock instead")
    boolean off;

    @Override
    public Integer call() {
        var r = opts.existing().call("lock", Map.of("value", !off));
        if (opts.json) Out.json(r);
        else Out.line(r.path("locked").asBoolean() ? "locked - the remote end can watch but do nothing" : "unlocked");
        return 0;
    }
}

@Command(name = "view-only", description = "Let the remote end watch but not type or run anything.")
final class ViewOnlyCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--off", description = "allow typing again")
    boolean off;

    @Override
    public Integer call() {
        var r = opts.existing().call("viewonly", Map.of("value", !off));
        if (opts.json) Out.json(r);
        else Out.line(r.path("viewOnly").asBoolean() ? "view only" : "the remote end may type again");
        return 0;
    }
}
