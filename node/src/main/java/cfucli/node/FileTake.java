package cfucli.node;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import cfucli.relay.SettingsStore;
import static cfucli.node.FileWire.*;

/** The answering half: an offer arriving, its chunks, and a request for a file this end holds.
 *  <p>
 *  Everything that can block - digesting, copying, waiting for a sync product to catch up - is put
 *  on a worker, because this runs on the thread that reads the session's frames and nothing else
 *  moves while it is busy. */
final class FileTake {

    final FileMover mover;
    final Map<String, FileRecv> inbound = new ConcurrentHashMap<>();

    FileTake(FileMover mover) {
        this.mover = mover;
    }

    void offer(JsonNode n) {
        var xferId = Wire.str(n, XFER, "");
        var mine = mover.wanted.remove(xferId);
        if (mine == null && mover.end.refuse("receiving a file")) {
            mover.abort(xferId, new SecurityException("the host has restricted this session"));
            return;
        }
        var name = Wire.str(n, NAME, "file");
        var route = Wire.str(n, ROUTE, FileRoute.RELAY);
        var size = Wire.l(n, SIZE, 0);
        var sha = Wire.str(n, SHA, "");
        var target = mine != null ? mine
                : FilePaths.resolveDest(Wire.str(n, DEST, null), name, Wire.bool(n, FORCE, false));
        mover.end.note((mine != null ? "get " : "put ") + xferId + ": " + name + " "
                       + FileRoute.human(size) + " over " + route + " -> " + target);
        switch (route) {
            case FileRoute.SAME_MACHINE -> copy(xferId, Paths.get(Wire.str(n, SOURCE, "")), target);
            case FileRoute.SHARED -> fromShared(xferId, Wire.str(n, RELATIVE, ""), target, size, sha,
                    Wire.l(n, WAIT_MS, FileMover.DEFAULT_WAIT.toMillis()));
            default -> inbound.put(xferId, new FileRecv(xferId, name, target, size, sha, (int) Wire.l(n, CHUNKS, 1)));
        }
    }

    void chunk(JsonNode n) {
        var xferId = Wire.str(n, XFER, "");
        var recv = inbound.get(xferId);
        if (recv == null || recv.complete()) return;
        recv.chunk(Base64.getDecoder().decode(Wire.str(n, DATA, "")));
        if (!recv.complete()) return;
        mover.submit(xferId, () -> {
            var placed = recv.finish();
            inbound.remove(xferId);
            mover.complete(xferId, placed, recv.bytes, recv.millis(), FileRoute.RELAY);
        });
    }

    void request(JsonNode n) {
        var xferId = Wire.str(n, XFER, "");
        if (mover.end.refuse("reading a file")) {
            mover.abort(xferId, new SecurityException("the host has restricted this session"));
            return;
        }
        var source = FilePaths.require(Wire.str(n, PATH, null));
        var size = FilePaths.size(source);
        var plan = FileRoute.choose(Wire.str(n, VIA, FileRoute.AUTO), source.getFileName().toString(), size,
                mover.threshold(), mover.end.sameMachine(), FileStage.available(mover.end.settings()),
                Wire.bool(n, SHARED, false));
        var force = Wire.bool(n, FORCE, false);
        var waitMs = Wire.l(n, WAIT_MS, FileMover.DEFAULT_WAIT.toMillis());
        mover.end.note("get " + xferId + ": " + source + " " + FileRoute.human(size) + " - " + plan.why());
        mover.submit(xferId, () -> FileSend.run(mover.end, xferId, source, "", plan, force, waitMs));
    }

    void copy(String xferId, Path source, Path target) {
        mover.submit(xferId, () -> {
            var startedAt = System.currentTimeMillis();
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot copy " + source + " to " + target, e);
            }
            mover.complete(xferId, target, FilePaths.size(target),
                    System.currentTimeMillis() - startedAt, FileRoute.SAME_MACHINE);
        });
    }

    void fromShared(String xferId, String relative, Path target, long size, String sha, long waitMs) {
        mover.submit(xferId, () -> {
            var root = FileStage.root(mover.end.settings());
            if (root == null) {
                throw new IllegalStateException("no shared exchange folder is configured on this end -"
                        + " set largeFileExchangeDir in " + SettingsStore.path());
            }
            var startedAt = System.currentTimeMillis();
            var staged = FileStage.await(root, relative, size, sha, Duration.ofMillis(waitMs));
            try {
                Files.copy(staged, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot copy " + staged + " to " + target, e);
            }
            FileStage.sweep(root, xferId);
            mover.complete(xferId, target, FilePaths.size(target),
                    System.currentTimeMillis() - startedAt, FileRoute.SHARED);
        });
    }

    void drop(String xferId) {
        var r = inbound.remove(xferId);
        if (r != null) r.close();
    }

    void closeAll() {
        inbound.values().forEach(FileRecv::close);
        inbound.clear();
    }
}
