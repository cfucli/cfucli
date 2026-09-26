package cfucli.relay;

import module java.base;

/** JSON lines, one thread per connection and the connection kept open, bound either to the
 *  loopback (a same-machine viewer) or to every interface (a LAN viewer, addressed by whichever
 *  candidate answers).
 *  <p>
 *  Kept open on purpose: the whole reason this exists is to get a keystroke across in
 *  microseconds, and a TCP handshake per frame would spend more time than the work. A read
 *  blocks for up to twenty seconds, so it also needs a thread that nothing else is waiting on. */
public final class LocalServer implements AutoCloseable {

    final LocalStore store;
    final ServerSocket socket;
    final String token;
    final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        var t = new Thread(r, "local-relay");
        t.setDaemon(true);
        return t;
    });

    volatile boolean running = true;

    LocalServer(LocalStore store, ServerSocket socket, String token) {
        this.store = store;
        this.socket = socket;
        this.token = token;
    }

    public static LocalServer start(LocalStore store, String token) {
        return start(store, token, false);
    }

    /** @param wildcard false binds the loopback only, for a same-machine viewer; true binds every
     *          interface, so a viewer on the LAN can reach whichever address of this machine's it
     *          happens to be routed through. */
    public static LocalServer start(LocalStore store, String token, boolean wildcard) {
        try {
            var s = wildcard ? new ServerSocket(0, 64) : new ServerSocket(0, 64, InetAddress.getLoopbackAddress());
            var server = new LocalServer(store, s, token);
            Thread.ofPlatform().name(wildcard ? "lan-accept" : "local-accept").daemon().start(server::accept);
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot open the " + (wildcard ? "LAN" : "local") + " relay socket", e);
        }
    }

    public int port() {
        return socket.getLocalPort();
    }

    void accept() {
        while (running) {
            try {
                var client = socket.accept();
                client.setTcpNoDelay(true);
                workers.submit(() -> serve(client));
            } catch (IOException e) {
                if (running) System.err.println("[local] accept failed: " + e.getMessage());
            }
        }
    }

    void serve(Socket client) {
        try (client;
             var in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             var out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
            for (var line = in.readLine(); line != null; line = in.readLine()) {
                out.write(LocalWire.line(handle(line)));
                out.write('\n');
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    com.fasterxml.jackson.databind.node.ObjectNode handle(String line) {
        try {
            var req = LocalWire.parse(line);
            if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                    LocalWire.str(req, "token", "").getBytes(StandardCharsets.UTF_8))) {
                return fail("this local session did not issue that token");
            }
            return LocalOps.apply(store, req);
        } catch (RuntimeException e) {
            return fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    static com.fasterxml.jackson.databind.node.ObjectNode fail(String message) {
        return LocalWire.obj().put("ok", false).put("error", message);
    }

    @Override
    public void close() {
        if (!running) return;
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        workers.shutdownNow();
    }
}
