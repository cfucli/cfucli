package cfucli.relay;

import module java.base;

/** Live check, against the configured Upstash database, of what hung the 2026-10-03 session: a
 *  backlog on the relay too big to come back in one read. Upstash refuses any reply over 10MB, and
 *  the reader used to ask for 256 entries and retry that same read for ever.
 *  <p>
 *  The viewer writes 60 frames of 400K BEFORE the host starts reading - about 33MB on the relay,
 *  so even the new 32-entry batch is refused and has to halve itself. Then it checks the other
 *  three fixes: the viewer's stream has an expiry, the reader has trimmed what it consumed, and the
 *  sweep gives a key without an expiry one. Cleans up after itself.
 *  <p>
 *  mvn -pl relay exec:java -Dexec.mainClass=cfucli.relay.RelayBacklogProbe -Dexec.classpathScope=test [-Dexec.args=rest] */
public final class RelayBacklogProbe {

    static final int FRAMES = 60, FRAME_BYTES = 400 * 1024;

    public static void main(String[] args) throws Exception {
        var settings = SettingsStore.load();
        var t = args.length > 0 && "rest".equals(args[0])
                ? RestTransport.open(settings.restUrl(), settings.restToken())
                : TransportFactory.open(settings).transport();
        say("transport  : " + t.name());
        var sessionId = Ids.newSessionId();
        var password = Ids.newPassword();
        var stray = Channels.NS + ":x:probe-" + sessionId + ":v2h";
        try {
            var host = HostIdentity.create(sessionId, password);
            Handshake.announce(t, host, "backlog-probe-host", "sh");
            var viewerKeys = Handshake.join(t, sessionId, password);
            var arrival = Handshake.awaitViewer(t, host, null, Duration.ofSeconds(10));
            check(arrival != null, "host never saw the viewer");

            var v2h = Channels.stream(sessionId, Direction.VIEWER_TO_HOST);
            try (var viewLink = new RelayLink(t, sessionId, Role.VIEWER, viewerKeys, RelayTransport.FIRST_ID);
                 var hostLink = new RelayLink(t, sessionId, Role.HOST, arrival.keys(), RelayTransport.FIRST_ID)) {
                viewLink.start(f -> {}, e -> say("viewer err : " + e));

                var t0 = System.nanoTime();
                var rnd = new Random(1);
                var sent = new ArrayList<byte[]>();
                for (var i = 0; i < FRAMES; i++) {
                    var b = new byte[FRAME_BYTES];
                    rnd.nextBytes(b);
                    sent.add(b);
                    viewLink.send(FrameType.FILE_CHUNK, b);
                }
                say("backlog    : " + FRAMES + " x " + FRAME_BYTES / 1024 + "K written in " + ms(t0) + " ms");
                var ttl = t.ttl(v2h);
                say("v2h ttl    : " + ttl + "s");
                check(ttl > 0, "the viewer's stream has no expiry");

                var got = new ArrayBlockingQueue<Frame>(FRAMES + 1);
                var errors = new AtomicInteger();
                var t1 = System.nanoTime();
                hostLink.start(got::add, e -> {
                    errors.incrementAndGet();
                    say("host err   : " + e);
                });
                for (var i = 0; i < FRAMES; i++) {
                    var f = got.poll(60, TimeUnit.SECONDS);
                    check(f != null, "host stalled after " + i + " of " + FRAMES + " frames");
                    check(Arrays.equals(sent.get(i), f.payload()), "frame " + i + " corrupted");
                }
                say("drained    : all " + FRAMES + " frames intact in " + ms(t1) + " ms, " + errors.get() + " errors reported");
                check(errors.get() == 0, "the oversize reply surfaced as an error instead of being halved away");

                Thread.sleep(500);
                var left = t.keys(v2h).isEmpty() ? 0 : xlen(t, v2h);
                say("after trim : " + left + " entries left on the relay");
                check(left < FRAMES / 2, "the reader did not trim what it consumed");
            }

            Sessions.end(t, sessionId);
            Sessions.dropStreams(t, sessionId);
            check(t.keys(Channels.NS + ":x:" + sessionId + ":*").isEmpty(), "streams survived dropStreams");
            say("end        : both streams deleted, meta kept with ENDED");

            t.append(stray, new byte[]{1}, 10);
            check(t.ttl(stray) == -1, "setup: the stray stream already has an expiry");
            var fixed = Sessions.sweep(t);
            say("sweep      : gave " + fixed + " key(s) an expiry; stray now " + t.ttl(stray) + "s");
            check(t.ttl(stray) > 0, "sweep left the stray stream without an expiry");
            say("");
            say("ALL CHECKS PASSED");
        } finally {
            Sessions.purge(t, sessionId);
            t.delete(stray);
            t.close();
        }
    }

    /** Through the read path rather than a new interface method: counts what a fresh reader sees. */
    static int xlen(RelayTransport t, String stream) {
        var n = 0;
        var from = RelayTransport.FIRST_ID;
        while (true) {
            var batch = t.read(stream, from, 4, null);
            if (batch.isEmpty()) return n;
            n += batch.size();
            from = batch.getLast().id();
        }
    }

    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static String ms(long since) {
        return Long.toString((System.nanoTime() - since) / 1_000_000);
    }

    static void say(String s) {
        System.out.println(s);
    }

    private RelayBacklogProbe() {}
}
