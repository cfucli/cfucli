package cfucli.node;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;

/** How everything else reaches a node. Starts one if none is running, because an agent should not
 *  have to know that a daemon exists. */
public final class NodeClient {

    public static final Duration START_TIMEOUT = Duration.ofSeconds(45);

    final String node;

    public NodeClient(String node) {
        this.node = node == null || node.isBlank() ? "default" : node;
    }

    public String node() {
        return node;
    }

    public boolean running() {
        return NodeInfo.read(node).filter(NodeInfo::processAlive).isPresent();
    }

    public JsonNode call(String verb, Map<String, Object> args) {
        return call(verb, args, Duration.ofMinutes(10));
    }

    public JsonNode call(String verb, Map<String, Object> args, Duration timeout) {
        var info = NodeInfo.read(node).filter(NodeInfo::processAlive)
                .orElseThrow(() -> new IllegalStateException("no node '" + node + "' is running"));
        try (var s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), info.port()), 5000);
            s.setSoTimeout((int) Math.min(Integer.MAX_VALUE, timeout.toMillis()));
            var out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            out.write(Wire.line(Wire.request(verb, args)));
            out.write('\n');
            out.flush();
            var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            var line = in.readLine();
            if (line == null) throw new IllegalStateException("node '" + node + "' closed the connection without answering");
            var res = Wire.parse(line);
            if (!res.path("ok").asBoolean(false)) {
                throw new NodeException(res.path("error").asText("the node refused the request"));
            }
            return res.get("result");
        } catch (SocketTimeoutException e) {
            throw new IllegalStateException("node '" + node + "' did not answer '" + verb + "' in time", e);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot reach node '" + node + "'", e);
        }
    }

    /** Launches a node in the background and waits for its port file. */
    public NodeClient ensureRunning(Path javaHome, Path jar) {
        if (running()) return this;
        var java = (javaHome == null ? Paths.get(System.getProperty("java.home")) : javaHome)
                .resolve("bin").resolve(cfucli.relay.Platform.javaExecutable(false));
        var cmd = new ArrayList<String>(List.of(java.toString(), "-cp", jar.toString(),
                "cfucli.node.Node", "--node", node));
        try {
            var log = cfucli.relay.Home.subdir("run").resolve("node-" + node + ".log").toFile();
            new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log).start();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start a node", e);
        }
        var deadline = Instant.now().plus(START_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (running()) return this;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("started a node but it never opened its port - see "
                + cfucli.relay.Home.subdir("run").resolve("node-" + node + ".log"));
    }
}
