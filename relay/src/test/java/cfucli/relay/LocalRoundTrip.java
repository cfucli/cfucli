package cfucli.relay;

import module java.base;

/** What the loopback path actually costs, measured rather than asserted. Run it with
 *  {@code mvn exec:java -Dexec.mainClass=cfucli.relay.LocalRoundTrip
 *  -Dexec.classpathScope=test -pl relay}.
 *  <p>
 *  The number that matters is the third one: a reader already blocked in {@code read}, and how
 *  long a keystroke takes to reach it. That is the figure the output window was traded against -
 *  sixty milliseconds of coalescing on the relay buys a tenfold cut in metered commands, and on
 *  this path there is nothing to buy. */
public final class LocalRoundTrip {

    public static void main(String[] args) throws Exception {
        var relay = LocalRelay.start("999999999", "probe");
        var direct = relay.transport();
        var client = LocalClientTransport.connect(relay.endpoint());
        var stream = Channels.stream("999999999", Direction.HOST_TO_VIEWER);
        try {
            System.out.println("local relay on port " + relay.port() + ", endpoint " + LocalEndpoint.path("999999999"));
            System.out.println("output window  direct=" + direct.outputWindow().toMillis() + "ms  client="
                               + client.outputWindow().toMillis() + "ms  (relay default is 60ms)");

            time("append, in-process   ", 2000, i -> direct.append(stream, ("x" + i).getBytes(StandardCharsets.UTF_8), 20_000));
            time("append, over socket  ", 2000, i -> client.append(stream, ("y" + i).getBytes(StandardCharsets.UTF_8), 20_000));
            time("getMeta, over socket ", 2000, i -> client.getMeta(Channels.meta("999999999")));

            blockedRead(direct, client, stream);
            wrongToken(relay);
            fifo(direct, client);
        } finally {
            client.close();
            relay.close();
        }
    }

    /** The keystroke case: a reader is already waiting, so this is delivery latency and nothing else. */
    static void blockedRead(RelayTransport host, RelayTransport viewer, String stream) throws Exception {
        var cursor = viewer.lastId(stream);
        var samples = 200;
        var total = 0L;
        var worst = 0L;
        for (var i = 0; i < samples; i++) {
            var from = cursor;
            var got = new AtomicLong();
            var delivered = new AtomicReference<List<StreamRecord>>(List.of());
            var reader = Thread.ofPlatform().start(() -> {
                var rs = viewer.read(stream, from, 256, Duration.ofSeconds(5));
                got.set(System.nanoTime());
                delivered.set(rs);
            });
            Thread.sleep(2);
            var sent = System.nanoTime();
            host.append(stream, ("k" + i).getBytes(StandardCharsets.UTF_8), 20_000);
            reader.join();
            if (delivered.get().isEmpty()) throw new IllegalStateException("a blocked read was never woken");
            cursor = delivered.get().getLast().id();
            var took = got.get() - sent;
            total += took;
            worst = Math.max(worst, took);
        }
        System.out.printf(Locale.ROOT, "blocked read wakeup   mean %.3f ms   worst %.3f ms   over %d keystrokes%n",
                total / (double) samples / 1e6, worst / 1e6, samples);
    }

    static void wrongToken(LocalRelay relay) {
        var forged = new LocalEndpoint(relay.sessionId(), relay.port(), 0, "probe", 0, "not-the-token");
        try {
            LocalClientTransport.connect(forged).ping();
            System.out.println("REFUSAL CHECK FAILED - a wrong token was accepted");
        } catch (RuntimeException e) {
            System.out.println("wrong token refused   " + e.getMessage());
        }
    }

    /** Order is the whole contract: frames are decrypted against a per-direction nonce sequence,
     *  so one delivered out of order is one that cannot be read at all. */
    static void fifo(RelayTransport host, RelayTransport viewer) {
        var stream = Channels.stream("999999999", Direction.VIEWER_TO_HOST);
        for (var i = 0; i < 500; i++) host.append(stream, Integer.toString(i).getBytes(StandardCharsets.UTF_8), 20_000);
        var read = viewer.read(stream, RelayTransport.FIRST_ID, 1000, Duration.ZERO);
        var ordered = true;
        for (var i = 0; i < read.size(); i++) {
            if (!Integer.toString(i).equals(new String(read.get(i).payload(), StandardCharsets.UTF_8))) ordered = false;
        }
        System.out.println("order and count       " + read.size() + " of 500 read back, in order: " + ordered);
    }

    static void time(String label, int reps, java.util.function.IntConsumer op) {
        for (var i = 0; i < 200; i++) op.accept(i);
        var start = System.nanoTime();
        for (var i = 0; i < reps; i++) op.accept(i);
        var each = (System.nanoTime() - start) / (double) reps / 1e6;
        System.out.printf(Locale.ROOT, "%s %.4f ms each over %d%n", label, each, reps);
    }

    private LocalRoundTrip() {}
}
