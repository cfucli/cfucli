package cfucli.relay;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** One kept-open line to a {@link LocalServer}. Used by a single thread at a time, which is what
 *  lets request and response be a bare pair of lines with no ids to correlate. */
final class LocalConn implements AutoCloseable {

    final Socket socket;
    final BufferedReader in;
    final BufferedWriter out;

    LocalConn(Socket socket, BufferedReader in, BufferedWriter out) {
        this.socket = socket;
        this.in = in;
        this.out = out;
    }

    static LocalConn open(int port) {
        try {
            var s = new Socket();
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 3000);
            s.setTcpNoDelay(true);
            return new LocalConn(s,
                    new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)),
                    new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot reach the local session on port " + port, e);
        }
    }

    JsonNode call(ObjectNode req, Duration timeout) {
        try {
            socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, timeout.toMillis()));
            out.write(LocalWire.line(req));
            out.write('\n');
            out.flush();
            var line = in.readLine();
            if (line == null) throw new IllegalStateException("the local session closed the connection");
            var res = LocalWire.parse(line);
            if (!res.path("ok").asBoolean(false)) {
                throw new IllegalStateException(res.path("error").asText("the local session refused the request"));
            }
            return res;
        } catch (IOException e) {
            throw new UncheckedIOException("local session call failed", e);
        }
    }

    boolean alive() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
