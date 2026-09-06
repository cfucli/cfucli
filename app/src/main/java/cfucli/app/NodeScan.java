package cfucli.app;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import cfucli.node.NodeClient;
import cfucli.node.NodeInfo;
import cfucli.relay.Home;

/** What is running on this machine, found by reading the port files the nodes already write.
 *  <p>
 *  The tray therefore holds nothing. It can be killed and started again at any moment without
 *  touching a live session, and a node works perfectly well with no tray at all - which is what
 *  keeps the headless and agent paths honest. */
public final class NodeScan {

    public static List<NodeCard> scan() {
        var out = new ArrayList<NodeCard>();
        try (var files = Files.list(Home.subdir("run"))) {
            for (var f : files.sorted().toList()) {
                var name = f.getFileName().toString();
                if (!name.startsWith("node-") || !name.endsWith(".json")) continue;
                var node = name.substring("node-".length(), name.length() - ".json".length());
                NodeInfo.read(node).filter(NodeInfo::processAlive).map(NodeScan::describe).ifPresent(out::add);
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    static NodeCard describe(NodeInfo info) {
        var card = new NodeCard().node(info.node()).port(info.port()).pid(info.pid());
        try {
            var status = new NodeClient(info.node()).call("status", Map.of(), Duration.ofSeconds(4));
            return fill(card, status);
        } catch (RuntimeException e) {
            return card.role("unreachable").detail("running but not answering - " + Dialogs.reason(e));
        }
    }

    static NodeCard fill(NodeCard card, JsonNode status) {
        card.hasWindow(status.path("hasWindow").asBoolean(false))
                .windowVisible(status.path("windowVisible").asBoolean(false))
                .transport(status.path("transport").asText("unopened"));
        var host = status.get("host");
        var viewer = status.get("viewer");
        if (host != null && !host.isNull()) {
            return card.role("host")
                    .sessionId(host.path("sessionId").asText())
                    .prettyId(host.path("prettyId").asText())
                    .connected(host.path("connected").asBoolean(false))
                    .locked(host.path("locked").asBoolean(false))
                    .viewOnly(host.path("viewOnly").asBoolean(false))
                    .shell(host.path("shell").asText(null))
                    .detail("sharing " + host.path("shell").asText("a shell") + "  ·  "
                            + (host.path("connected").asBoolean(false) ? "a viewer is connected" : "nobody connected yet")
                            + "  ·  " + host.path("columns").asInt(0) + "x" + host.path("rows").asInt(0));
        }
        if (viewer != null && !viewer.isNull()) {
            return card.role("viewer")
                    .sessionId(viewer.path("sessionId").asText())
                    .prettyId(cfucli.relay.Ids.prettySessionId(viewer.path("sessionId").asText()))
                    .connected(viewer.path("usable").asBoolean(false))
                    .locked(viewer.path("locked").asBoolean(false))
                    .viewOnly(viewer.path("viewOnly").asBoolean(false))
                    .shell(viewer.path("shell").asText(null))
                    .detail("watching " + viewer.path("hostName").asText("a host") + "  ·  "
                            + viewer.path("detail").asText(""));
        }
        return card.role("idle").detail("no session - ready for host or join");
    }

    public static String title(NodeCard c) {
        var id = c.prettyId() == null || c.prettyId().isBlank() ? "" : "  ·  " + c.prettyId();
        return c.node() + id;
    }

    public static String pill(NodeCard c) {
        if (Boolean.TRUE.equals(c.locked())) return "locked";
        if (Boolean.TRUE.equals(c.viewOnly())) return "view only";
        return switch (c.role() == null ? "idle" : c.role()) {
            case "host" -> Boolean.TRUE.equals(c.connected()) ? "live" : "waiting";
            case "viewer" -> Boolean.TRUE.equals(c.connected()) ? "connected" : "host silent";
            case "unreachable" -> "not answering";
            default -> "idle";
        };
    }

    public static String pillStyle(NodeCard c) {
        if (Boolean.TRUE.equals(c.locked())) return "pill-locked";
        if (Boolean.TRUE.equals(c.viewOnly())) return "pill-view";
        if ("unreachable".equals(c.role())) return "pill-locked";
        return Boolean.TRUE.equals(c.connected()) ? "pill-live" : "pill-wait";
    }

    private NodeScan() {}
}
