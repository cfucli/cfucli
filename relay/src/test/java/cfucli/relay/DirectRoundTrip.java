package cfucli.relay;

import module java.base;

/** The LAN path, exercised the same way {@link LocalRoundTrip} exercises the loopback: a real
 *  wildcard-bound listener, a client that connects to one of this machine's own LAN addresses
 *  rather than 127.0.0.1, and a check that {@code local()}/{@code direct()} come out the way
 *  {@link FileRoute} needs them to - false and true respectively, because two different machines
 *  is the scenario this path exists for even when, as here, they happen to be the same box.
 *  <p>
 *  Run with {@code mvn exec:java -Dexec.mainClass=cfucli.relay.DirectRoundTrip
 *  -Dexec.classpathScope=test -pl relay}. */
public final class DirectRoundTrip {

    public static void main(String[] args) throws Exception {
        var hosts = DirectCandidates.localAddresses();
        System.out.println("candidate addresses: " + hosts);
        if (hosts.isEmpty()) {
            System.out.println("no non-loopback IPv4 address on this machine - nothing to probe");
            return;
        }

        var lan = LanRelay.startQuietly();
        if (lan == null) throw new IllegalStateException("LanRelay did not start");
        try {
            System.out.println("lan relay bound on port " + lan.port());

            var address = new InetSocketAddress(hosts.get(0), lan.port());
            var client = LocalClientTransport.connect(address, lan.token());
            try {
                System.out.println("connected to " + address + "  name=" + client.name()
                                   + "  local()=" + client.local() + "  direct()=" + client.direct());
                if (client.local()) throw new IllegalStateException("a LAN address must not report local()");
                if (!client.direct()) throw new IllegalStateException("a LAN transport must report direct()");

                var stream = "probe-stream";
                var id = client.append(stream, "hello".getBytes(StandardCharsets.UTF_8), 100);
                var back = client.read(stream, RelayTransport.FIRST_ID, 10, Duration.ZERO);
                System.out.println("round trip: appended " + id + ", read back "
                                   + new String(back.getFirst().payload(), StandardCharsets.UTF_8));

                try {
                    LocalClientTransport.connect(address, "not-the-token");
                    System.out.println("REFUSAL CHECK FAILED - a wrong token was accepted");
                } catch (RuntimeException e) {
                    System.out.println("wrong token refused: " + e.getMessage());
                }
            } finally {
                client.close();
            }

            var advertised = new DirectCandidates.Advertised(hosts, lan.port(), lan.token());
            var raced = DirectCandidates.connect(advertised);
            System.out.println("race across " + hosts.size() + " candidate(s): "
                               + (raced.isPresent() ? "won by " + raced.get().name() : "nobody answered"));
            raced.ifPresent(LocalClientTransport::close);
        } finally {
            lan.close();
        }
    }

    private DirectRoundTrip() {}
}
