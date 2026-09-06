package cfucli.node;

import module java.base;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.arcadedb.database.Database;
import cfucli.record.*;
import cfucli.relay.*;

/** The machine being shared. Owns the shell, advertises the session, and serves whichever viewer
 *  currently holds the password. A viewer may come and go; the shell does not restart.
 *  <p>
 *  It listens on more than one door at once. There is always a loopback one, because a viewer on
 *  this same machine should not pay a datacentre round trip for a keystroke, and there is usually
 *  a relay one as well for the far end this tool exists to reach. Whichever a viewer arrives
 *  through becomes the link, and the output window follows it - immediate on the loopback,
 *  coalesced on the relay, where every frame is a metered command. */
public final class HostSession implements AutoCloseable, FileEnd {

    /** What the recording is chunked at, whatever the link is doing. A row per keystroke would
     *  make a transaction per keystroke, which is a database problem rather than a latency one. */
    public static final Duration RECORD_WINDOW = Duration.ofMillis(60);

    static final String ESC = String.valueOf((char) 27), CLEAR_AND_HOME = ESC + "[2J" + ESC + "[H";

    final List<RelayTransport> transports;
    final LocalRelay localRelay;
    final Database db;
    final HostIdentity identity;
    final PtyHost pty;
    final Recorder recorder;
    final Settings settings;
    final OutputCoalescer toRecorder, toLink;
    final ConsentGate consent;
    final HostExec exec = new HostExec(this);
    final FileMover files = new FileMover(this);
    final List<Consumer<byte[]>> taps = new CopyOnWriteArrayList<>();
    final List<Consumer<String>> activity = new CopyOnWriteArrayList<>();
    final List<Runnable> watchers = new CopyOnWriteArrayList<>();
    final List<Consumer<String>> expiries = new CopyOnWriteArrayList<>();
    final Map<RelayTransport, String> viewerKeys = new ConcurrentHashMap<>();

    volatile RelayLink link;
    volatile RelayTransport linkTransport;
    volatile boolean locked, viewOnly, closed;
    volatile int columns = PtyHost.DEFAULT_COLUMNS, rows = PtyHost.DEFAULT_ROWS;
    volatile Instant lastActivity = Instant.now();
    volatile Supplier<String> snapshot;
    volatile SessionExpiry expiry;

    Thread watcher, beater;

    HostSession(List<RelayTransport> transports, LocalRelay localRelay, Database db, HostIdentity identity,
                PtyHost pty, Recorder recorder, Settings settings, ConsentGate consent) {
        this.transports = transports;
        this.localRelay = localRelay;
        this.db = db;
        this.identity = identity;
        this.pty = pty;
        this.recorder = recorder;
        this.settings = settings;
        this.consent = consent;
        this.toRecorder = new OutputCoalescer(0, RECORD_WINDOW, this::record);
        this.toLink = new OutputCoalescer(0, null, this::publish);
        pty.onOutput(this::fromShell);
    }

    /** {@code relay} is null for a session that is never to leave this machine, which is the
     *  shape an agent wants for a local console: nothing is announced, nothing is metered, and
     *  the Upstash credentials are not even read. */
    public static HostSession start(Database db, String node, String shell, Path cwd, RelayTransport relay,
                                    String relayName, Settings settings, ConsentGate consent) {
        var identity = HostIdentity.create(Ids.newSessionId(), Ids.newPassword());
        var local = LocalRelay.start(identity.sessionId(), node);
        var transports = new ArrayList<RelayTransport>();
        transports.add(local.transport());
        if (relay != null) transports.add(relay);
        var pty = PtyHost.start(shell, cwd, PtyHost.DEFAULT_COLUMNS, PtyHost.DEFAULT_ROWS);
        var where = describe(relayName);
        var recorder = Recorder.open(db, identity.sessionId(), "host", hostName(), shell, where,
                settings == null || !Boolean.FALSE.equals(settings.recordOutput()));
        var s = new HostSession(List.copyOf(transports), local, db, identity, pty, recorder, settings, consent);
        for (var t : s.transports) Handshake.announce(t, identity, hostName(), shell);
        recorder.control("host session announced on " + where);
        s.beater = Thread.ofPlatform().name("host-beat").daemon().start(s::beat);
        s.watcher = Thread.ofPlatform().name("host-watch").daemon().start(s::watchForViewers);
        s.expiry = SessionExpiry.start(s, settings);
        return s;
    }

    static String describe(String relayName) {
        return relayName == null || relayName.isBlank() ? "local loopback only" : "local loopback and " + relayName;
    }

    public String sessionId() {
        return identity.sessionId();
    }

    public String password() {
        return identity.password();
    }

    public boolean connected() {
        return link != null;
    }

    /** Which door the viewer actually came through, which is the one that decides latency. */
    public String linkTransportName() {
        var t = linkTransport;
        return t == null ? null : t.name();
    }

    public boolean localOnly() {
        return transports.size() == 1 && transports.getFirst().local();
    }

    public int localPort() {
        return localRelay.port();
    }

    public boolean locked() {
        return locked;
    }

    public boolean viewOnly() {
        return viewOnly;
    }

    public Recorder recorder() {
        return recorder;
    }

    public HostExec exec() {
        return exec;
    }

    public PtyHost pty() {
        return pty;
    }

    public FileMover files() {
        return files;
    }

    @Override
    public Settings settings() {
        return settings;
    }

    /** True when the viewer arrived over the loopback, which means both ends are this computer and
     *  a file transfer has a filesystem rather than a wire to cross. */
    @Override
    public boolean sameMachine() {
        var t = linkTransport;
        return t != null && t.local();
    }

    @Override
    public boolean refuse(String what) {
        return refuseIfRestricted(what);
    }

    /** Never consulted on this side: a viewer's request carries its own answer, which cannot be
     *  stale the way a remembered one could. */
    @Override
    public boolean peerShared() {
        return false;
    }

    @Override
    public boolean attached() {
        return link != null;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    /** What this session will end of, in words, or null when nothing is enforced - which is the
     *  honest answer for a local-only console. */
    public String expiryNote() {
        var e = expiry;
        return e == null ? null : e.summary();
    }

    /** A job of the far end's still running counts as the session being in use, so a build nobody
     *  is typing at is not cut off from underneath the person waiting on it. */
    public boolean busy() {
        return exec.busy();
    }

    void touch() {
        lastActivity = Instant.now();
    }

    /** Told when the idle or maximum-lifetime limit closes this session, so whoever owns it can
     *  clear it away - the session cannot remove itself from the node that holds it. */
    public void onExpiry(Consumer<String> listener) {
        expiries.add(listener);
    }

    void expire(String why) {
        note(why + " - ending it");
        for (var e : expiries) {
            try {
                e.accept(why);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    /** Raw shell bytes, and only ever those. */
    public void tap(Consumer<byte[]> listener) {
        taps.add(listener);
        pty.onOutput(listener);
    }

    /** What the tool itself did - a command an agent ran, a viewer arriving, the lock going on.
     *  <p>
     *  A separate channel from {@link #tap}, and that separation is the point. This chatter used
     *  to be written into the shell's own output, which put the tool's log and the program's
     *  output on one stream where nothing downstream could tell them apart - it corrupted a
     *  transcript being read, and it would land in the middle of whatever the shell was drawing.
     *  A terminal carries the terminal; this carries everything about the terminal. */
    public void onActivity(Consumer<String> listener) {
        activity.add(listener);
    }

    /** Called whenever anything the banner shows has moved: a viewer arriving or leaving, the lock
     *  going on, the geometry changing. */
    public void onChange(Runnable r) {
        watchers.add(r);
    }

    /** What a rejoining viewer is caught up with. Supplied by the window, because the screen as
     *  currently rendered only exists where there is an emulator to render it. */
    public void snapshot(Supplier<String> s) {
        snapshot = s;
    }

    /** The rendered screen, when there is a window to render it. Null on a headless node. */
    public String screen() {
        var s = snapshot;
        return s == null ? null : s.get();
    }

    public void locked(boolean b) {
        locked = b;
        note(b ? "remote locked out by the host" : "remote unlocked by the host");
        flag(Meta.LOCKED, b);
        announceState();
        changed();
    }

    public void viewOnly(boolean b) {
        viewOnly = b;
        note(b ? "remote set to view only" : "remote may type again");
        flag(Meta.VIEW_ONLY, b);
        announceState();
        changed();
    }

    /** Local typing: goes to the shell, and the shell's echo reaches the remote the same way any
     *  other output does. */
    public void typeLocally(byte[] bytes) {
        pty.write(bytes);
        touch();
        recorder.input("host", new String(bytes, StandardCharsets.UTF_8));
    }

    /** The host window changed shape. The host is authoritative about geometry - a viewer that
     *  reflowed the shared shell to its own window would garble the screen of the person whose
     *  machine it is - so the new size goes to the shell and then out as a STATE frame for the
     *  viewer to match. */
    public void resizeLocally(int columns, int rows) {
        if (columns <= 0 || rows <= 0 || (columns == this.columns && rows == this.rows)) return;
        this.columns = columns;
        this.rows = rows;
        pty.resize(columns, rows);
        announceState();
        changed();
    }

    void changed() {
        for (var w : watchers) {
            try {
                w.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    void announceState() {
        var n = Wire.obj();
        n.put("columns", columns);
        n.put("rows", rows);
        n.put("locked", locked);
        n.put("viewOnly", viewOnly);
        n.put("hostName", hostName());
        n.put("shell", pty.command());
        // So a viewer can plan a large transfer without staging a file first and finding out
        // afterwards that this end cannot read it.
        n.put("sharedExchange", FileStage.available(settings));
        send(FrameType.STATE, Wire.line(n).getBytes(StandardCharsets.UTF_8));
    }

    void flag(String field, boolean b) {
        for (var t : transports) {
            try {
                t.putMeta(Channels.meta(sessionId()), Map.of(field, b ? "1" : "0"), Meta.TTL);
            } catch (RuntimeException e) {
                recorder.error("cannot update session flags on " + t.name() + ": " + e);
            }
        }
    }

    void fromShell(byte[] chunk) {
        toRecorder.add(chunk);
        toLink.add(chunk);
    }

    void record(byte[] chunk) {
        recorder.output("host", new String(chunk, StandardCharsets.UTF_8));
    }

    void publish(byte[] chunk) {
        var l = link;
        if (l == null) return;
        try {
            l.send(FrameType.OUTPUT, chunk);
        } catch (RuntimeException e) {
            recorder.error("cannot publish output: " + e);
        }
    }

    void beat() {
        while (!closed) {
            for (var t : transports) {
                try {
                    Handshake.beat(t, sessionId());
                    t.touch(Channels.stream(sessionId(), Direction.HOST_TO_VIEWER), Meta.STREAM_TTL);
                } catch (RuntimeException e) {
                    problem("heartbeat failed on " + t.name() + ": " + e);
                }
            }
            sleep(Meta.HEARTBEAT);
        }
    }

    /** Round-robin across the doors, each asked at its own rhythm. The loopback can be asked ten
     *  times a second for nothing; the relay charges a command for every look, so it is asked
     *  every couple of seconds and no oftener. */
    void watchForViewers() {
        var due = new HashMap<RelayTransport, Long>();
        while (!closed) {
            var shortest = Long.MAX_VALUE;
            for (var t : transports) {
                if (closed) return;
                var interval = Math.max(50, t.pollInterval().toMillis());
                shortest = Math.min(shortest, interval);
                if (System.currentTimeMillis() < due.getOrDefault(t, 0L)) continue;
                due.put(t, System.currentTimeMillis() + interval);
                offerViewer(t);
            }
            sleep(Duration.ofMillis(shortest == Long.MAX_VALUE ? 1000 : shortest));
        }
    }

    void offerViewer(RelayTransport t) {
        try {
            var arrival = Handshake.pollViewer(t, identity, viewerKeys.get(t));
            if (arrival == null) return;
            if (!consent.allow(sessionId(), "a viewer holding the one-time password is joining")) {
                // Remembering the refused key is what stops the loop re-offering the same arrival
                // on its next pass and prompting the human forever. A genuine retry generates a
                // fresh keypair, so it still gets asked.
                viewerKeys.put(t, arrival.viewerKey());
                note("viewer refused by the host");
                changed();
                return;
            }
            attach(t, arrival);
        } catch (SecurityException e) {
            recorder.error("rejected a viewer: " + e.getMessage());
        } catch (RuntimeException e) {
            if (!closed) recorder.error("viewer watch failed on " + t.name() + ": " + e);
        }
    }

    void attach(RelayTransport t, ViewerArrival arrival) {
        var old = link;
        if (old != null) old.close();
        viewerKeys.put(t, arrival.viewerKey());
        var fresh = new RelayLink(t, sessionId(), Role.HOST, arrival.keys(), RelayTransport.LAST_ID);
        fresh.start(this::onFrame, e -> recorder.error("relay: " + e));
        link = fresh;
        linkTransport = t;
        toLink.window(t.outputWindow());
        note("viewer connected over " + t.name());
        fresh.send(FrameType.HELLO, "connected to " + hostName() + " running " + pty.command() + "\r\n");
        announceState();
        replayScreen();
        changed();
    }

    /** A fresh key agreement per visit means the relay's own history is unreadable to a rejoining
     *  viewer, so the screen it starts from has to be sent again rather than replayed.
     *  <p>
     *  Framed here rather than by whoever rendered it: this is the one caller writing into an
     *  emulator, and an agent asking for the same screen wants text it can read. */
    void replayScreen() {
        var screen = screen();
        if (screen == null || screen.isBlank()) return;
        try {
            send(FrameType.OUTPUT, (CLEAR_AND_HOME + screen).getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            recorder.error("cannot send the screen snapshot: " + e);
        }
    }

    void onFrame(Frame f) {
        lastActivity = Instant.now();
        switch (f.type()) {
            case INPUT -> onInput(f);
            case RESIZE -> onResize(f);
            case EXEC_REQUEST -> exec.request(f);
            case EXEC_CANCEL -> exec.cancel(f);
            case FILE_OFFER, FILE_CHUNK, FILE_DONE, FILE_REQUEST -> files.onFrame(f);
            case PING -> send(FrameType.PONG, new byte[0]);
            case BYE -> onBye();
            default -> recorder.control("ignored frame " + f.type());
        }
    }

    void onBye() {
        note("viewer disconnected");
        var l = link;
        link = null;
        linkTransport = null;
        if (l != null) l.close();
        changed();
    }

    void onInput(Frame f) {
        if (refuseIfRestricted("typing")) return;
        pty.write(f.payload());
        recorder.input("viewer", f.asText());
    }

    void onResize(Frame f) {
        var n = ExecWire.read(f.payload());
        resizeLocally(Wire.i(n, "columns", columns), Wire.i(n, "rows", rows));
    }

    /** Says what the tool did, to both ends' activity logs and to neither end's terminal. It goes
     *  out as a CONTROL frame rather than as OUTPUT so the far end can keep the two apart too.
     *  <p>
     *  Deliberately not recorded here: the recorder already writes a control line when a job
     *  starts and when it ends, and it redacts the command while doing it. Echoing into the
     *  recording as well produced two near-identical lines per job, one of them unredacted. */
    void echo(String text) {
        for (var a : activity) {
            try {
                a.accept(text);
            } catch (RuntimeException ignored) {
            }
        }
        send(FrameType.CONTROL, text.getBytes(StandardCharsets.UTF_8));
    }

    /** The same, for things the far end learns another way and does not need told twice. */
    /** Something went wrong, said where a person can see it rather than only in the recording. */
    void problem(String text) {
        recorder.error(text);
        for (var a : activity) {
            try {
                a.accept(text);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void note(String text) {
        recorder.event(Streams.CONTROL, "host", text, null);
        for (var a : activity) {
            try {
                a.accept(text);
            } catch (RuntimeException ignored) {
            }
        }
    }

    boolean refuseIfRestricted(String what) {
        if (!locked && !viewOnly) return false;
        var why = locked ? "the host has locked this session" : "this session is view only";
        send(FrameType.ERROR, why.getBytes(StandardCharsets.UTF_8));
        note("refused remote " + what + ": " + why);
        return true;
    }

    @Override
    public void send(FrameType type, byte[] payload) {
        var l = link;
        if (l == null) return;
        try {
            l.send(type, payload);
        } catch (RuntimeException e) {
            recorder.error("cannot send " + type + ": " + e);
        }
    }

    static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getProperty("user.name", "host");
        }
    }

    /** Guarded step by step for the reason given on {@link NodeService#close()}: the endpoint file
     *  and the pty must go even if something above them throws. */
    @Override
    public void close() {
        closed = true;
        quietly(() -> {
            var e = expiry;
            if (e != null) e.close();
        });
        quietly(() -> {
            var l = link;
            if (l != null) l.close();
        });
        quietly(toLink::close);
        quietly(toRecorder::close);
        quietly(files::close);
        quietly(exec::close);
        quietly(() -> recorder.end("host closed the session"));
        for (var t : transports) quietly(() -> Sessions.end(t, sessionId()));
        quietly(localRelay::close);
        quietly(pty::close);
        quietly(this::changed);
    }

    static void quietly(Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            System.err.println("[node] " + t);
        }
    }
}
