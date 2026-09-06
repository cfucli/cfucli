package cfucli.node;

import module java.base;
import cfucli.relay.FrameType;
import static cfucli.node.FileWire.*;

/** The sending half of a transfer, whichever end is doing the sending: a viewer pushing a file at
 *  the host, or a host answering a request for one.
 *  <p>
 *  Runs off the frame-dispatch thread, always. Reading a hundred megabytes to digest it, waiting on
 *  a sync product, and pushing two hundred encrypted chunks are all things that must not stop the
 *  session's own traffic while they happen. */
final class FileSend {

    static void run(FileEnd end, String xferId, Path source, String dest, FilePlan plan, boolean force, long waitMs) {
        var name = source.getFileName().toString();
        var offer = Wire.obj();
        offer.put(XFER, xferId).put(NAME, name).put(DEST, dest == null ? "" : dest)
                .put(SIZE, plan.size()).put(ROUTE, plan.route()).put(WHY, plan.why())
                .put(CHUNKS, plan.chunks()).put(CHUNK_BYTES, plan.chunkBytes())
                .put(FORCE, force).put(WAIT_MS, waitMs);
        if (plan.sameMachine()) {
            // Nothing crosses anything: the far end is this filesystem, and the copy it makes is
            // verified by the operating system. Digesting a large file to prove that would be work
            // done for nothing.
            offer.put(SOURCE, source.toString()).put(SHA, "");
            end.send(FrameType.FILE_OFFER, bytes(offer));
            return;
        }
        offer.put(SHA, FilePaths.sha256(source));
        if (plan.shared()) {
            stage(end, xferId, source, name, offer);
            return;
        }
        end.send(FrameType.FILE_OFFER, bytes(offer));
        chunks(end, xferId, source, plan);
    }

    static void stage(FileEnd end, String xferId, Path source, String name, com.fasterxml.jackson.databind.node.ObjectNode offer) {
        var root = FileStage.root(end.settings());
        if (root == null) {
            throw new IllegalStateException("no shared exchange folder is configured on this end -"
                    + " set largeFileExchangeDir in " + cfucli.relay.SettingsStore.path());
        }
        var relative = FileStage.relative(xferId, name);
        FileStage.stage(root, relative, source);
        offer.put(RELATIVE, relative);
        end.send(FrameType.FILE_OFFER, bytes(offer));
        end.note("staged " + name + " in " + root.resolve(FileStage.DIR) + " - the far end is waiting for it to sync");
    }

    /** readNBytes rather than read: a stream is entitled to hand back a short read at any time, and
     *  a short chunk here would leave the receiver's count right and its file wrong. */
    static void chunks(FileEnd end, String xferId, Path source, FilePlan plan) {
        try (var in = new BufferedInputStream(Files.newInputStream(source), 1 << 16)) {
            var buf = new byte[plan.chunkBytes()];
            for (var i = 0; i < plan.chunks(); i++) {
                var n = in.readNBytes(buf, 0, buf.length);
                var payload = n == buf.length ? buf : Arrays.copyOf(buf, Math.max(0, n));
                var c = Wire.obj();
                c.put(XFER, xferId).put(INDEX, i).put(DATA, Base64.getEncoder().encodeToString(payload));
                end.send(FrameType.FILE_CHUNK, bytes(c));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + source, e);
        }
    }

    private FileSend() {}
}
