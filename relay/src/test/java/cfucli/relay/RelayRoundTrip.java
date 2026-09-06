package cfucli.relay;

import module java.base;

/** Live check against the configured Upstash database. Run with:
 *  mvn -pl relay exec:java -Dexec.mainClass=cfucli.relay.RelayRoundTrip -Dexec.classpathScope=test */
public final class RelayRoundTrip {

    public static void main(String[] args) throws Exception {
        var settings = SettingsStore.load();
        say("settings   : " + SettingsStore.path());
        say("redisUrl   : " + mask(settings.redisUrl()));
        say("restUrl    : " + mask(settings.restUrl()));

        var t0 = System.nanoTime();
        var choice = TransportFactory.open(settings);
        say("transport  : " + choice.description() + "  (" + ms(t0) + " ms)");
        choice.notes().forEach(n -> say("             note: " + n));

        var t = choice.transport();
        var sessionId = Ids.newSessionId();
        var password = Ids.newPassword();
        say("session    : " + Ids.prettySessionId(sessionId) + "   password " + Ids.prettyPassword(password));

        try {
            var host = HostIdentity.create(sessionId, password);
            Handshake.announce(t, host, "smoke-test-host", "cmd.exe");
            say("announced  : " + Sessions.status(t, sessionId).state());

            var wrong = Sessions.status(t, "000000000");
            say("unknown id : " + wrong.state() + " - " + wrong.detail());

            var t1 = System.nanoTime();
            var viewerKeys = Handshake.join(t, sessionId, password);
            var arrival = Handshake.awaitViewer(t, host, null, Duration.ofSeconds(10));
            if (arrival == null) throw new IllegalStateException("host never saw the viewer");
            var hostKeys = arrival.keys();
            say("handshake  : agreed in " + ms(t1) + " ms");

            try (var hostLink = new RelayLink(t, sessionId, Role.HOST, hostKeys, RelayTransport.FIRST_ID);
                 var viewLink = new RelayLink(t, sessionId, Role.VIEWER, viewerKeys, RelayTransport.FIRST_ID)) {

                var atViewer = new ArrayBlockingQueue<Frame>(16);
                var atHost = new ArrayBlockingQueue<Frame>(16);
                viewLink.start(atViewer::add, e -> say("viewer err : " + e));
                hostLink.start(atHost::add, e -> say("host err   : " + e));

                var t2 = System.nanoTime();
                hostLink.send(FrameType.OUTPUT, "Microsoft Windows [Version 10.0]\r\nC:\\>");
                var got = atViewer.poll(20, TimeUnit.SECONDS);
                check(got != null, "viewer received nothing from host");
                check(got.type() == FrameType.OUTPUT, "wrong type " + got.type());
                say("host->view : " + ms(t2) + " ms  " + got.size() + "B  " + oneLine(got.asText()));

                var t3 = System.nanoTime();
                viewLink.send(FrameType.INPUT, "dir /b\r\n");
                var back = atHost.poll(20, TimeUnit.SECONDS);
                check(back != null, "host received nothing from viewer");
                check("dir /b\r\n".equals(back.asText()), "payload mangled: " + back.asText());
                say("view->host : " + ms(t3) + " ms  " + back.size() + "B  " + oneLine(back.asText()));

                var t4 = System.nanoTime();
                var big = "x".repeat(64_000);
                hostLink.send(FrameType.OUTPUT, big);
                var bulk = atViewer.poll(30, TimeUnit.SECONDS);
                check(bulk != null && big.equals(bulk.asText()), "64KB frame did not survive the round trip");
                say("64KB frame : " + ms(t4) + " ms");

                var unicode = "Paramashiva नित्यानन्द — ok";
                hostLink.send(FrameType.OUTPUT, unicode);
                var uni = atViewer.poll(20, TimeUnit.SECONDS);
                check(uni != null, "no frame arrived for the unicode check within 20s");
                check(unicode.equals(uni.asText()), "unicode mangled: sent " + codepoints(unicode)
                        + " got " + codepoints(uni.asText()));
                say("unicode    : intact (" + uni.size() + "B utf-8, " + unicode.codePointCount(0, unicode.length()) + " codepoints)");
            }

            var wrongPw = attemptWrongPassword(t, sessionId);
            say("wrong pw   : " + wrongPw);

            say("status     : " + Sessions.status(t, sessionId).detail());
            Sessions.end(t, sessionId);
            say("after end  : " + Sessions.status(t, sessionId).state());
            say("");
            say("ALL CHECKS PASSED");
        } finally {
            Sessions.purge(t, sessionId);
            t.close();
        }
    }

    static String attemptWrongPassword(RelayTransport t, String sessionId) {
        try {
            Handshake.join(t, sessionId, "AAAAAAAA");
            return "FAILED - a wrong password was accepted";
        } catch (SecurityException e) {
            return "rejected (" + e.getMessage() + ")";
        }
    }

    static String codepoints(String s) {
        return s.codePoints().mapToObj(c -> "U+" + Integer.toHexString(c)).collect(Collectors.joining(" "));
    }

    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static String ms(long since) {
        return Long.toString((System.nanoTime() - since) / 1_000_000);
    }

    static String oneLine(String s) {
        var t = s.replace("\r", "").replace("\n", " ");
        return t.length() > 40 ? t.substring(0, 40) + "..." : t;
    }

    static String mask(String s) {
        if (s == null || s.isBlank()) return "(absent)";
        var scheme = s.contains("://") ? s.substring(0, s.indexOf("://") + 3) : "";
        var tail = s.length() < 12 ? "" : s.substring(s.length() - 12);
        return scheme + "..." + tail;
    }

    static void say(String s) {
        System.out.println(s);
    }

    private RelayRoundTrip() {}
}
