package cfucli.relay;

import module java.base;

/** The host's LAN address book for one session, and the viewer's attempt to knock on it.
 *  <p>
 *  The host does not know which of its addresses the viewer can actually reach - a VPN adapter,
 *  a Docker bridge and the real Wi-Fi adapter all look equally plausible from here - so it offers
 *  every non-loopback IPv4 address it has and leaves the deciding to whichever one answers first.
 *  This rides on the relay meta the viewer was going to read anyway for the host's public key, so
 *  advertising it costs nothing beyond the handshake's own single {@code putMeta}/{@code getMeta}. */
public final class DirectCandidates {

    /** However many addresses are offered, the whole race is bounded by this - a candidate with
     *  nobody listening fails fast on its own connect timeout, but a machine with a dozen virtual
     *  adapters must not turn "try them all" into a multi-second stall before falling back to the
     *  relay, which is always going to work. */
    public static final Duration RACE_BUDGET = Duration.ofSeconds(2);

    public record Advertised(List<String> hosts, int port, String token) {
        public boolean isEmpty() {
            return hosts.isEmpty() || port <= 0 || token == null || token.isBlank();
        }
    }

    /** Every IPv4 address this machine has that is not the loopback and not link-local (the
     *  169.254/16 range a NIC assigns itself when nothing else configured it) - candidates a
     *  viewer elsewhere on the LAN might route to. Best-effort: an interface that cannot be
     *  enumerated is skipped rather than failing the whole list. */
    public static List<String> localAddresses() {
        var out = new LinkedHashSet<String>();
        try {
            var nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                var nic = nics.nextElement();
                try {
                    if (!nic.isUp() || nic.isLoopback()) continue;
                } catch (SocketException e) {
                    continue;
                }
                var addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    var a = addrs.nextElement();
                    if (a instanceof Inet4Address v4 && !v4.isLoopbackAddress() && !v4.isLinkLocalAddress()) {
                        out.add(v4.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return List.copyOf(out);
    }

    public static void announce(RelayTransport relay, String sessionId, List<String> hosts, int port, String token) {
        if (hosts.isEmpty()) return;
        var fields = new LinkedHashMap<String, String>();
        fields.put(Meta.DIRECT_HOSTS, String.join(",", hosts));
        fields.put(Meta.DIRECT_PORT, Integer.toString(port));
        fields.put(Meta.DIRECT_TOKEN, token);
        relay.putMeta(Channels.meta(sessionId), fields, Meta.TTL);
    }

    /** Null when the host published nothing - an older host, one with no usable address, or one
     *  the settings told not to bother. */
    public static Advertised read(Map<String, String> meta) {
        var raw = meta.get(Meta.DIRECT_HOSTS);
        var token = meta.get(Meta.DIRECT_TOKEN);
        var portStr = meta.get(Meta.DIRECT_PORT);
        if (raw == null || raw.isBlank() || token == null || portStr == null) return null;
        int port;
        try {
            port = Integer.parseInt(portStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        var hosts = Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        return hosts.isEmpty() ? null : new Advertised(hosts, port, token);
    }

    /** Races a connection attempt at every candidate and returns whichever answers first, or empty
     *  when none does within {@link #RACE_BUDGET} - never throws, because failing to find a direct
     *  path is the ordinary case the relay exists to cover. */
    public static Optional<LocalClientTransport> connect(Advertised advertised) {
        if (advertised == null || advertised.isEmpty()) return Optional.empty();
        var pool = Executors.newFixedThreadPool(Math.max(1, advertised.hosts().size()), r -> {
            var t = new Thread(r, "direct-probe");
            t.setDaemon(true);
            return t;
        });
        try {
            var tasks = advertised.hosts().stream()
                    .<Callable<LocalClientTransport>>map(host -> () ->
                            LocalClientTransport.connect(new InetSocketAddress(host, advertised.port()), advertised.token()))
                    .toList();
            return Optional.of(pool.invokeAny(tasks, RACE_BUDGET.toMillis(), TimeUnit.MILLISECONDS));
        } catch (ExecutionException | TimeoutException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            pool.shutdownNow();
        }
    }

    private DirectCandidates() {}
}
