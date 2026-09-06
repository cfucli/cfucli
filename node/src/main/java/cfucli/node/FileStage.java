package cfucli.node;

import module java.base;
import cfucli.relay.Settings;

/** The folder both machines already have mounted, used as the route for anything too big to push
 *  through a message relay.
 *  <p>
 *  Only the RELATIVE path travels on the wire. The two machines mount the same synced folder at
 *  different absolute paths - that is the normal case with Google Drive, where one end is a letter
 *  drive and the other is under a home directory - so an absolute path from the sender would be
 *  meaningless at the receiver.
 *  <p>
 *  Arrival is decided by the digest, not by the file appearing. A sync product writes the file over
 *  some seconds and gives no signal when it has finished, and a sidecar marker is no help because
 *  nothing guarantees it syncs after the payload. Size first because it is free, then SHA-256. */
public final class FileStage {

    public static final String DIR = "cfucli-xfer";
    public static final Duration LOOK = Duration.ofSeconds(2);

    public static Path root(Settings s) {
        var configured = s == null ? null : s.largeFileExchangeDir();
        if (configured == null || configured.isBlank()) return null;
        var p = Paths.get(configured.trim()).toAbsolutePath().normalize();
        return Files.isDirectory(p) ? p : null;
    }

    public static boolean available(Settings s) {
        return root(s) != null;
    }

    public static String relative(String xferId, String name) {
        return DIR + "/" + xferId + "/" + name;
    }

    public static Path stage(Path root, String relative, Path source) {
        var target = resolve(root, relative);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot stage " + source.getFileName() + " in " + root, e);
        }
    }

    public static Path await(Path root, String relative, long size, String sha, Duration wait) {
        var target = resolve(root, relative);
        var deadline = System.nanoTime() + (wait == null ? Duration.ofMinutes(5) : wait).toNanos();
        var seen = false;
        while (true) {
            if (Files.isRegularFile(target)) {
                seen = true;
                if (FilePaths.size(target) == size && sha.equalsIgnoreCase(FilePaths.sha256(target))) return target;
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(seen
                        ? relative + " appeared in " + root + " but is still " + FileRoute.human(FilePaths.size(target))
                          + " of " + FileRoute.human(size) + " - the sync has not finished; try again with a longer --timeout"
                        : relative + " never appeared in " + root + " - check that both machines really share that folder,"
                          + " and that this one has finished syncing");
            }
            HostSession.sleep(LOOK);
        }
    }

    /** Best effort: the staged copy is a duplicate of a file that is now in its place, and a sync
     *  product may hold it open for a moment after writing it. The parent goes too when it empties,
     *  because this folder belongs to the person whose Drive it is. */
    public static void sweep(Path root, String xferId) {
        if (root == null || xferId == null) return;
        var dir = root.resolve(DIR).resolve(xferId);
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(FilePaths::deleteQuietly);
        } catch (IOException | RuntimeException ignored) {
        }
        FilePaths.deleteQuietly(root.resolve(DIR));
    }

    static Path resolve(Path root, String relative) {
        var p = root;
        for (var part : relative.split("/")) {
            if (part.isBlank() || "..".equals(part)) throw new IllegalArgumentException("bad staging path " + relative);
            p = p.resolve(part);
        }
        return p;
    }

    private FileStage() {}
}
