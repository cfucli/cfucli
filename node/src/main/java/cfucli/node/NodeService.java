package cfucli.node;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import cfucli.record.*;
import cfucli.relay.*;

/** Everything the cli can ask for, in one dispatch. The node holds the relay connection and the
 *  recording store; the cli holds neither, which is what keeps a per-call process cheap and stops
 *  two processes fighting over ArcadeDB's directory lock. */
public final class NodeService implements AutoCloseable {

    final String node;
    final Settings settings;
    final RecordDb store;
    final Housekeeping housekeeping;

    volatile TransportChoice transport, viewerTransport;
    volatile HostSession host;
    volatile ViewerSession viewer;
    volatile Runnable shutdownHook;
    volatile ConsentGate consent = ConsentGate.PASSWORD_IS_CONSENT;
    volatile WindowControl window;
    volatile Runnable sessionWatcher;

    public NodeService(String node, Settings settings) {
        this.node = node;
        this.settings = settings;
        this.store = RecordDb.open(node);
        this.housekeeping = Housekeeping.start(this::sweep);
    }

    /** What the timer runs. Whatever is live here is excluded by name rather than by age, because a
     *  node up for longer than the retention window would otherwise delete the recording it is
     *  still writing. */
    String sweep() {
        var days = settings.logRetentionDays() == null ? 14 : settings.logRetentionDays();
        if (days <= 0) return null;
        var removed = Retention.forgetOlderThan(store.db(), days, liveSessions());
        return removed == 0 ? null : "dropped " + removed + " recording(s) older than " + days + " days";
    }

    Set<String> liveSessions() {
        var live = new LinkedHashSet<String>();
        var h = host;
        var v = viewer;
        if (h != null) live.add(h.sessionId());
        if (v != null) live.add(v.sessionId());
        return live;
    }

    public void onShutdown(Runnable r) {
        shutdownHook = r;
    }

    /** Adds to the hook rather than replacing it. The server sets the first one - close the socket,
     *  remove the port file - and a window adds its own on top, because a node stopped by the cli
     *  must take its window with it. A window left behind by a dead node looks live and is not. */
    public void alsoOnShutdown(Runnable r) {
        var first = shutdownHook;
        shutdownHook = first == null ? r : () -> {
            try {
                first.run();
            } finally {
                r.run();
            }
        };
    }

    /** The window supplies a real prompt; headless nodes keep the default, where holding the
     *  one-time password is the consent. */
    public void consent(ConsentGate gate) {
        consent = gate == null ? ConsentGate.PASSWORD_IS_CONSENT : gate;
    }

    public void window(WindowControl w) {
        window = w;
    }

    /** Fired after a session starts or ends by any route, so a window that is already open lights
     *  up when the agent runs join against the same node. */
    public void onSessionChange(Runnable r) {
        sessionWatcher = r;
    }

    public HostSession host() {
        return host;
    }

    public ViewerSession viewer() {
        return viewer;
    }

    public Settings settings() {
        return settings;
    }

    public String nodeName() {
        return node;
    }

    void sessionChanged() {
        var r = sessionWatcher;
        if (r != null) try {
            r.run();
        } catch (RuntimeException ignored) {
        }
    }

    public JsonNode dispatch(String verb, JsonNode a) {
        return switch (verb) {
            case "status" -> status(a);
            case "host" -> startHost(a);
            case "join" -> join(a);
            case "keys" -> keys(a);
            case "exec" -> exec(a);
            case "wait" -> waitFor(a);
            case "cancel" -> cancel(a);
            case "screen" -> screen();
            case "put" -> put(a);
            case "get" -> get(a);
            case "locals" -> locals();
            case "lock" -> setLock(a);
            case "viewonly" -> setViewOnly(a);
            case "end" -> end();
            case "sessions" -> sessions(a);
            case "summary" -> summary(a);
            case "tail" -> tail(a);
            case "events" -> events(a);
            case "grep" -> grep(a);
            case "jobs" -> jobsList(a);
            case "job" -> job(a);
            case "forget" -> forget(a);
            case "scrub" -> scrub(a);
            case "retain" -> retain(a);
            case "show" -> window(true);
            case "hide" -> window(false);
            case "shutdown" -> shutdown();
            default -> throw new IllegalArgumentException("unknown verb '" + verb + "'");
        };
    }

    RelayTransport relay() {
        if (transport == null) {
            synchronized (this) {
                if (transport == null) transport = TransportFactory.open(settings);
            }
        }
        return transport.transport();
    }

    String transportName() {
        if (transport != null) return transport.description();
        var h = host;
        return h != null && h.localOnly() ? "not opened - this session is local only" : "unopened";
    }

    JsonNode status(JsonNode a) {
        var n = Wire.obj();
        n.put("node", node);
        n.put("pid", ProcessHandle.current().pid());
        n.put("store", store.location().toString());
        n.put("transport", transportName());
        var w = window;
        n.put("hasWindow", w != null);
        if (w != null) n.put("windowVisible", w.visible());
        var hk = Wire.obj();
        hk.put("runs", housekeeping.runs());
        hk.put("lastRunAt", housekeeping.lastRunAt());
        hk.put("lastResult", housekeeping.lastResult());
        hk.put("retentionDays", settings.logRetentionDays() == null ? 14 : settings.logRetentionDays());
        n.set("housekeeping", hk);
        var choice = transport;
        if (choice != null && !choice.notes().isEmpty()) {
            var notes = Wire.JSON.createArrayNode();
            choice.notes().forEach(notes::add);
            n.set("transportNotes", notes);
        }
        var h = host;
        var v = viewer;
        if (h != null) {
            var hn = Wire.obj();
            hn.put("sessionId", h.sessionId());
            hn.put("prettyId", Ids.prettySessionId(h.sessionId()));
            hn.put("connected", h.connected());
            hn.put("locked", h.locked());
            hn.put("viewOnly", h.viewOnly());
            hn.put("shell", h.pty().command());
            hn.put("shellAlive", h.pty().alive());
            hn.put("columns", h.columns());
            hn.put("rows", h.rows());
            hn.put("localOnly", h.localOnly());
            hn.put("localPort", h.localPort());
            hn.put("idleSeconds", Duration.between(h.lastActivity(), Instant.now()).toSeconds());
            hn.put("busy", h.busy());
            if (h.expiryNote() == null) hn.putNull("expires"); else hn.put("expires", h.expiryNote());
            hn.put("sharedExchange", FileStage.available(settings));
            hn.put("linkTransport", h.linkTransportName() == null ? "none" : h.linkTransportName());
            // Only when asked for. It is the host's own machine and their own password, but it
            // should never land in a transcript just because someone ran status.
            if (Wire.bool(a, "showPassword", false)) {
                hn.put("password", h.password());
                hn.put("prettyPassword", Ids.prettyPassword(h.password()));
            }
            n.set("host", hn);
        } else {
            n.putNull("host");
        }
        if (v != null) {
            var vn = Json.status(v.status());
            vn.put("sessionId", v.sessionId());
            var vt = viewerTransport;
            if (vt != null) {
                vn.put("transport", vt.description());
                vn.put("local", vt.transport().local());
            }
            if (v.peekRefusal() != null) vn.put("lastRefusal", v.peekRefusal());
            n.set("viewer", vn);
        } else {
            n.putNull("viewer");
        }
        return n;
    }

    /** {@code local} means this session is never announced anywhere off this machine: no relay
     *  connection, no credentials read, nothing metered. It is the shape for an agent's console -
     *  a shell in a window a human can watch, driven from the cli next door. */
    JsonNode startHost(JsonNode a) {
        if (host != null) throw new IllegalStateException("this node is already hosting session " + host.sessionId());
        var shell = Wire.str(a, "shell", settings.defaultShell());
        var cwd = Wire.str(a, "cwd", null);
        var localOnly = Wire.bool(a, "local", false);
        var relay = localOnly ? null : relay();
        host = HostSession.start(store.db(), node, shell, cwd == null ? null : Paths.get(cwd), relay,
                localOnly ? null : transportName(), settings, consent);
        host.onExpiry(why -> expired(why));
        sessionChanged();
        var n = Wire.obj();
        n.put("sessionId", host.sessionId());
        n.put("prettyId", Ids.prettySessionId(host.sessionId()));
        n.put("password", host.password());
        n.put("prettyPassword", Ids.prettyPassword(host.password()));
        n.put("shell", shell);
        n.put("local", localOnly);
        n.put("transport", HostSession.describe(localOnly ? null : transportName()));
        return n;
    }

    JsonNode join(JsonNode a) {
        if (viewer != null) throw new IllegalStateException("this node is already viewing session " + viewer.sessionId());
        var sessionId = Ids.normalise(Wire.str(a, "sessionId", ""));
        var password = Ids.normalise(Wire.str(a, "password", ""));
        if (!Ids.isSessionId(sessionId)) throw new IllegalArgumentException("session id must be nine digits");
        var choice = TransportFactory.forViewer(settings, sessionId);
        viewerTransport = choice;
        try {
            viewer = ViewerSession.join(choice.transport(), store.db(), sessionId, password, choice.description(),
                    settings);
        } catch (RuntimeException e) {
            closeViewerTransport();
            throw e;
        }
        sessionChanged();
        var n = Wire.obj();
        n.put("sessionId", sessionId);
        n.put("transport", choice.description());
        n.put("local", choice.transport().local());
        if (!choice.notes().isEmpty()) {
            var notes = Wire.JSON.createArrayNode();
            choice.notes().forEach(notes::add);
            n.set("transportNotes", notes);
        }
        n.set("status", Json.status(viewer.status()));
        return n;
    }

    JsonNode keys(JsonNode a) {
        var text = Wire.str(a, "text", "");
        var v = viewer;
        var h = host;
        var n = Wire.obj();
        n.put("sent", text.length());
        if (v != null) {
            var refusal = v.sendKeys(text, Duration.ofMillis(Wire.l(a, "confirmMs", 500)));
            n.put("accepted", refusal == null);
            if (refusal != null) n.put("refused", refusal);
        } else if (h != null) {
            h.typeLocally(text.getBytes(StandardCharsets.UTF_8));
            n.put("accepted", true);
        } else {
            throw new IllegalStateException("no session on this node - run host, or join one");
        }
        return n;
    }

    /** A viewer runs it on the far machine; a host runs it here. Both give back the same shape -
     *  exact exit code, stdout and stderr apart - and both echo the transcript into the shared
     *  view, so the human watching the window sees what the agent did either way. */
    JsonNode exec(JsonNode a) {
        var command = Wire.str(a, "command", "");
        if (command.isBlank()) throw new IllegalArgumentException("exec needs a command");
        var cwd = Wire.str(a, "cwd", null);
        var stdin = Wire.str(a, "stdin", null);
        var detach = Wire.bool(a, "detach", false);
        var timeout = Duration.ofMillis(Wire.l(a, "timeoutMs", ExecRunner.DEFAULT_TIMEOUT.toMillis()));
        var v = viewer;
        if (v != null) {
            if (detach) return Wire.obj().put("jobId", v.execDetached(command, cwd, timeout, stdin)).put("detached", true);
            var result = Json.exec(v.exec(command, cwd, timeout, stdin));
            var refusal = v.takeUnreportedRefusal();
            if (refusal != null) result.put("refused", refusal);
            return result;
        }
        var h = host;
        if (h == null) throw new IllegalStateException("no session on this node - start one with host, or join one");
        if (detach) {
            return Wire.obj().put("jobId", h.exec().submit(command, cwd, timeout, stdin, "agent"))
                    .put("detached", true).put("where", "host");
        }
        return Json.exec(h.exec().run(command, cwd, timeout, stdin, "agent")).put("where", "host");
    }

    /** Picks up a detached job wherever it was started. */
    JsonNode waitFor(JsonNode a) {
        var jobId = Wire.str(a, "jobId", "");
        if (jobId.isBlank()) throw new IllegalArgumentException("wait needs a job id");
        var wait = Duration.ofMillis(Wire.l(a, "waitMs", Duration.ofMinutes(5).toMillis()));
        var v = viewer;
        if (v != null && v.tracking(jobId)) return Json.exec(v.await(jobId, wait));
        var h = host;
        if (h != null) return Json.exec(h.exec().await(jobId, wait)).put("where", "host");
        throw new NoSuchElementException("no job " + jobId + " is being tracked on this node");
    }

    JsonNode cancel(JsonNode a) {
        var jobId = Wire.str(a, "jobId", "");
        var v = viewer;
        if (v != null) {
            v.cancel(jobId);
            return Wire.obj().put("cancelled", true);
        }
        var h = requireHost();
        return Wire.obj().put("cancelled", h.exec().cancel(jobId));
    }

    /** The screen as it is rendered right now, which is what an agent should look at before it
     *  reads a transcript. Only a window has an emulator, so a headless node says so. */
    JsonNode screen() {
        var h = host;
        var v = viewer;
        var text = h != null ? h.screen() : v != null ? v.screen() : null;
        var n = Wire.obj();
        if (h == null && v == null) throw new IllegalStateException("no session on this node");
        if (text == null) {
            n.put("hasScreen", false);
            n.put("detail", "node '" + node + "' is headless - there is no emulator here to render a screen. "
                            + "Use tail, or start the session in a window.");
            return n;
        }
        n.put("hasScreen", true);
        n.put("screen", text);
        if (h != null) {
            n.put("columns", h.columns());
            n.put("rows", h.rows());
        }
        return n;
    }

    /** Both are the viewer's verbs: a put pushes a file at the far machine, a get pulls one back.
     *  The route is chosen by size and by what both ends can reach, and the answer says which was
     *  taken - a transfer that quietly went the expensive way is worse than one that refused. */
    JsonNode put(JsonNode a) {
        var v = requireFarEnd("put");
        return v.files().put(Wire.str(a, "local", null), Wire.str(a, "remote", null), Wire.str(a, "via", null),
                Wire.bool(a, "force", false), waitOf(a));
    }

    JsonNode get(JsonNode a) {
        var v = requireFarEnd("get");
        return v.files().get(Wire.str(a, "remote", null), Wire.str(a, "local", null), Wire.str(a, "via", null),
                Wire.bool(a, "force", false), waitOf(a));
    }

    static Duration waitOf(JsonNode a) {
        return Duration.ofMillis(Wire.l(a, "waitMs", FileMover.DEFAULT_WAIT.toMillis()));
    }

    ViewerSession requireFarEnd(String verb) {
        var v = viewer;
        if (v != null) return v;
        if (host != null) {
            throw new IllegalStateException("this node hosts the session, so both of its ends are this machine -"
                    + " " + verb + " moves a file between two machines. Copy it here with exec, or run " + verb
                    + " on the node that joined.");
        }
        throw new IllegalStateException("no session on this node - join one first");
    }

    /** The idle or maximum-lifetime limit ran out. The session closes itself as far as it can and
     *  this clears it off the node, which the session cannot do for itself. */
    void expired(String why) {
        System.out.println("[node] " + node + ": " + why);
        try {
            end();
        } catch (RuntimeException ignored) {
        }
    }

    /** Sessions hosted on this machine right now, so a viewer can be pointed at one without
     *  anybody reading nine digits aloud. */
    JsonNode locals() {
        var out = Wire.JSON.createArrayNode();
        for (var e : LocalEndpoint.live()) {
            out.addObject().put("sessionId", e.sessionId()).put("prettyId", Ids.prettySessionId(e.sessionId()))
                    .put("node", e.node()).put("pid", e.pid()).put("port", e.port()).put("startedAt", e.startedAt());
        }
        return out;
    }

    JsonNode setLock(JsonNode a) {
        var h = requireHost();
        h.locked(Wire.bool(a, "value", true));
        return Wire.obj().put("locked", h.locked());
    }

    JsonNode setViewOnly(JsonNode a) {
        var h = requireHost();
        h.viewOnly(Wire.bool(a, "value", true));
        return Wire.obj().put("viewOnly", h.viewOnly());
    }

    JsonNode end() {
        var n = Wire.obj();
        var v = viewer;
        var h = host;
        if (v != null) {
            v.close();
            viewer = null;
            closeViewerTransport();
            n.put("viewerClosed", true);
        }
        if (h != null) {
            h.close();
            host = null;
            n.put("hostClosed", true);
        }
        if (n.isEmpty()) throw new IllegalStateException("no session on this node");
        sessionChanged();
        return n;
    }

    JsonNode sessions(JsonNode a) {
        var rows = SessionQueries.sessions(store.db(), Wire.i(a, "limit", 25));
        return Json.array(rows.stream().map(Json::session).toList());
    }

    JsonNode summary(JsonNode a) {
        return Json.summary(SessionQueries.summary(store.db(), sessionIdArg(a)));
    }

    /** Defaults to the shell's own streams. A transcript is read as one run of text, so mixing the
     *  tool's control lines into it puts the log back in the channel the window went to some
     *  trouble to keep it out of - "--streams all" asks for them explicitly. */
    JsonNode tail(JsonNode a) {
        var rows = SessionQueries.tail(store.db(), sessionIdArg(a),
                Streams.resolve(Wire.str(a, "streams", "shell")), Wire.i(a, "n", 40));
        return Json.array(rows.stream().map(Json::event).toList());
    }

    JsonNode events(JsonNode a) {
        var rows = SessionQueries.events(store.db(), sessionIdArg(a),
                Wire.boxedLong(a, "from"), Wire.boxedLong(a, "to"),
                Streams.resolve(Wire.str(a, "streams", null)), Wire.i(a, "limit", 500));
        return Json.array(rows.stream().map(Json::event).toList());
    }

    JsonNode grep(JsonNode a) {
        var matches = SessionGrep.grep(store.db(), sessionIdArg(a), Wire.str(a, "regex", "."),
                Streams.resolve(Wire.str(a, "streams", null)), Wire.i(a, "limit", 200),
                Wire.i(a, "context", 0), Wire.bool(a, "ignoreCase", false));
        return Json.array(matches.stream().map(Json::match).toList());
    }

    JsonNode jobsList(JsonNode a) {
        var id = sessionIdArg(a);
        var rows = Wire.bool(a, "failed", false)
                ? SessionQueries.failedJobs(store.db(), id, Wire.i(a, "limit", 200))
                : SessionQueries.jobs(store.db(), id, Wire.boxedInt(a, "exitCode"), Wire.i(a, "limit", 200));
        return Json.array(rows.stream().map(Json::job).toList());
    }

    JsonNode job(JsonNode a) {
        var jobId = Wire.str(a, "jobId", "");
        var row = SessionQueries.job(store.db(), jobId)
                .orElseThrow(() -> new NoSuchElementException("no job " + jobId + " in this node's store"));
        // A job id is unique across the store, so --session is a check rather than a lookup key.
        // It is accepted because "jobs --session X" is how the id was found, and a verb refusing
        // the flag its own index took is a papercut with no reason behind it.
        var scope = Ids.normalise(Wire.str(a, "sessionId", null));
        if (scope != null && !scope.isBlank() && !scope.equals(row.sessionId()))
            throw new NoSuchElementException("job " + jobId + " is in session " + row.sessionId()
                    + ", not " + scope);
        var n = Json.job(row);
        var events = SessionQueries.jobEvents(store.db(), jobId, Streams.resolve(Wire.str(a, "streams", null)),
                Wire.i(a, "limit", 2000));
        n.put("stdout", events.stream().filter(e -> Streams.EXEC_STDOUT.equals(e.stream()))
                .map(EventRow::text).collect(Collectors.joining()));
        n.put("stderr", events.stream().filter(e -> Streams.EXEC_STDERR.equals(e.stream()))
                .map(EventRow::text).collect(Collectors.joining()));
        return n;
    }

    JsonNode forget(JsonNode a) {
        return Wire.obj().put("eventsRemoved", Retention.forget(store.db(), sessionIdArg(a)));
    }

    JsonNode scrub(JsonNode a) {
        return Wire.obj().put("eventsScrubbed", Retention.scrub(store.db(), sessionIdArg(a)));
    }

    JsonNode retain(JsonNode a) {
        var days = Wire.i(a, "days", settings.logRetentionDays() == null ? 14 : settings.logRetentionDays());
        return Wire.obj().put("sessionsRemoved", Retention.forgetOlderThan(store.db(), days, liveSessions()))
                .put("days", days);
    }

    JsonNode window(boolean visible) {
        var w = window;
        if (w == null) return Wire.obj().put("window", false)
                .put("detail", "node '" + node + "' is headless - it was started without a window");
        if (visible) w.show(); else w.hide();
        return Wire.obj().put("window", true).put("visible", visible);
    }

    JsonNode shutdown() {
        var hook = shutdownHook;
        // Not a daemon: the JVM must not exit out from under the close, or the port file and the
        // local endpoint file are left behind for the next process to puzzle over.
        if (hook != null) Thread.ofPlatform().name("node-stop").start(() -> {
            HostSession.sleep(Duration.ofMillis(150));
            hook.run();
        });
        return Wire.obj().put("shuttingDown", true);
    }

    String sessionIdArg(JsonNode a) {
        var explicit = Ids.normalise(Wire.str(a, "sessionId", null));
        if (explicit != null && !explicit.isBlank()) return explicit;
        var v = viewer;
        if (v != null) return v.sessionId();
        var h = host;
        if (h != null) return h.sessionId();
        throw new IllegalStateException(noSession());
    }

    /** A dead end costs a round trip to escape, so the refusal carries the way out: the sessions
     *  this store actually holds. Reading a recording back after the session ended is the normal
     *  case, not an edge one - a node with no live session still has every byte of the old ones. */
    String noSession() {
        var recent = SessionQueries.sessions(store.db(), 5);
        if (recent.isEmpty()) return "no session on this node, and nothing is recorded here yet"
                + " - start one with: cfucli console";
        return "no session on this node. Pass --session with one of these, newest first:"
                + recent.stream().map(r -> System.lineSeparator() + "  " + r.sessionId()
                        + "  " + r.role() + "  " + (r.hostName() == null ? "" : r.hostName())
                        + (r.endedAt() == null ? "  live" : ""))
                        .collect(Collectors.joining());
    }

    ViewerSession requireViewer() {
        var v = viewer;
        if (v == null) throw new IllegalStateException("this node is not viewing a session - join one first");
        return v;
    }

    HostSession requireHost() {
        var h = host;
        if (h == null) throw new IllegalStateException("this node is not hosting a session");
        return h;
    }

    /** Only when it is the viewer's own - a local session opens one per join, where the relay is
     *  shared with the host and outlives any one viewer. */
    void closeViewerTransport() {
        var vt = viewerTransport;
        viewerTransport = null;
        if (vt == null) return;
        if (transport != null && vt.transport() == transport.transport()) return;
        try {
            vt.transport().close();
        } catch (RuntimeException ignored) {
        }
    }

    /** Every step guarded, and the store closed whatever happened above it.
     *  <p>
     *  It was not, and a single throw on the way down cost a process: closing the exec pool raised
     *  NoClassDefFoundError - the jar had been rebuilt underneath a running node, so a class it had
     *  never happened to load was gone - and that skipped the store shutdown, which left ArcadeDB's
     *  non-daemon threads running. The node printed "stopped" and stayed resident, holding the
     *  database it had just claimed to release. Shutdown is exactly where nothing may be skipped. */
    @Override
    public void close() {
        try {
            housekeeping.close();
        } catch (Throwable t) {
            System.err.println("[node] stopping housekeeping failed: " + t);
        }
        try {
            var v = viewer;
            if (v != null) v.close();
        } catch (Throwable t) {
            System.err.println("[node] closing the viewer failed: " + t);
        }
        try {
            closeViewerTransport();
        } catch (Throwable t) {
            System.err.println("[node] closing the viewer transport failed: " + t);
        }
        try {
            var h = host;
            if (h != null) h.close();
        } catch (Throwable t) {
            System.err.println("[node] closing the host session failed: " + t);
        }
        try {
            if (transport != null) transport.transport().close();
        } catch (Throwable t) {
            System.err.println("[node] closing the relay failed: " + t);
        }
        RecordDb.shutdown();
    }
}
