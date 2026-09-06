package cfucli.node;

import module java.base;

/** Where a file lands, and proof that it arrived intact. */
public final class FilePaths {

    /** A directory means "put it in there under its own name", which is what every other copy tool
     *  does. Nothing is overwritten without being asked twice, because a transfer that silently
     *  replaced something is not recoverable from the far end. */
    public static Path resolveDest(String dest, String name, boolean force) {
        var p = (dest == null || dest.isBlank()
                ? Paths.get(System.getProperty("user.home")).resolve(name)
                : Paths.get(dest.trim())).toAbsolutePath().normalize();
        var target = Files.isDirectory(p) ? p.resolve(name) : p;
        if (Files.exists(target) && !force) {
            throw new IllegalStateException(target + " already exists (" + FileRoute.human(size(target))
                                            + ") - pass --force to replace it");
        }
        var parent = target.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            throw new IllegalStateException("there is no directory " + parent + " to put " + name + " in");
        }
        return target;
    }

    public static Path require(String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("no file named");
        var p = Paths.get(path.trim()).toAbsolutePath().normalize();
        if (!Files.exists(p)) throw new NoSuchElementException(p + " does not exist");
        if (Files.isDirectory(p)) throw new IllegalArgumentException(p + " is a directory - name a file");
        if (!Files.isReadable(p)) throw new IllegalStateException(p + " cannot be read");
        return p;
    }

    /** The file name out of a path the OTHER machine wrote, so Paths.get cannot be trusted to split
     *  it - a Windows path handed to a POSIX runtime has no separators it recognises. */
    public static String nameOf(String path) {
        var p = path == null ? "" : path.replace('\\', '/');
        var cut = p.lastIndexOf('/');
        var name = cut < 0 ? p : p.substring(cut + 1);
        if (name.isBlank()) throw new IllegalArgumentException("cannot tell a file name from '" + path + "'");
        return name;
    }

    public static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }

    public static String sha256(Path p) {
        try (var in = new BufferedInputStream(Files.newInputStream(p), 1 << 16)) {
            var md = MessageDigest.getInstance("SHA-256");
            var buf = new byte[1 << 16];
            for (var n = in.read(buf); n > 0; n = in.read(buf)) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + p, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Beside the destination rather than in the system temp directory, so the final move is a
     *  rename on the same volume and cannot half-succeed. */
    public static Path tempBeside(Path target) {
        var parent = target.getParent() == null ? Paths.get(".") : target.getParent();
        return parent.resolve("." + target.getFileName() + ".ucli-part");
    }

    public static void place(Path temp, Path target) {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot put the file at " + target, e);
        }
    }

    public static void deleteQuietly(Path p) {
        try {
            if (p != null) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    private FilePaths() {}
}
