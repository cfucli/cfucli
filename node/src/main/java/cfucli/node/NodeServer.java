package cfucli.node;

import module java.base;

/** Loopback only, one JSON request per connection, each on its own thread.
 *  <p>
 *  Its own thread matters: a blocking exec can take minutes, and serving requests one at a time
 *  would mean nobody could ask for status - or press the lock - while a command was running. */
public final class NodeServer implements AutoCloseable {

    final String node;
    final NodeService service;
    final ServerSocket socket;
    final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        var t = new Thread(r, "node-req");
        t.setDaemon(true);
        return t;
    });

    final CountDownLatch finished = new CountDownLatch(1);

    volatile boolean running = true;

    NodeServer(String node, NodeService service, ServerSocket socket) {
        this.node = node;
        this.service = service;
        this.socket = socket;
    }

    public static NodeServer start(String node, NodeService service) {
        try {
            var s = new ServerSocket(0, 32, InetAddress.getLoopbackAddress());
            var server = new NodeServer(node, service, s);
            new NodeInfo(node, s.getLocalPort(), ProcessHandle.current().pid(), System.currentTimeMillis()).write();
            service.onShutdown(server::close);
            Thread.ofPlatform().name("node-accept").daemon().start(server::accept);
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot open the loopback socket", e);
        }
    }

    public int port() {
        return socket.getLocalPort();
    }

    void accept() {
        while (running) {
            try {
                var client = socket.accept();
                workers.submit(() -> serve(client));
            } catch (IOException e) {
                if (running) System.err.println("[node] accept failed: " + e.getMessage());
            }
        }
    }

    void serve(Socket client) {
        try (client;
             var in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             var out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
            var line = in.readLine();
            if (line == null) return;
            var response = handle(line);
            out.write(Wire.line(response));
            out.write('\n');
            out.flush();
        } catch (IOException ignored) {
        }
    }

    com.fasterxml.jackson.databind.node.ObjectNode handle(String line) {
        try {
            var req = Wire.parse(line);
            var verb = req.get("verb").asText();
            return Wire.ok(service.dispatch(verb, req.get("args")));
        } catch (RuntimeException e) {
            return Wire.fail(message(e));
        }
    }

    static String message(Throwable e) {
        var m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    /** Waits for the close to FINISH, not to begin. Polling the running flag returned the moment
     *  close set it, which let the main thread walk out while the store was still being shut. */
    public void awaitShutdown() {
        try {
            finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (!running) return;
        running = false;
        try {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            workers.shutdownNow();
            NodeInfo.remove(node);
            service.close();
        } finally {
            finished.countDown();
        }
    }
}
