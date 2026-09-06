package cfucli.node;

import module java.base;

/** One file arriving in chunks over the relay. Written to a part-file beside its destination so
 *  the last step is a rename on the same volume, and verified against the sender's digest before
 *  that rename happens - a truncated or reordered transfer must not become the file. */
final class FileRecv implements Closeable {

    final String xferId, name, sha;
    final long size;
    final int chunks;
    final Path target, temp;
    final long startedAt = System.currentTimeMillis();

    OutputStream out;
    int received;
    long bytes;

    FileRecv(String xferId, String name, Path target, long size, String sha, int chunks) {
        this.xferId = xferId;
        this.name = name;
        this.target = target;
        this.size = size;
        this.sha = sha;
        this.chunks = chunks;
        this.temp = FilePaths.tempBeside(target);
    }

    void chunk(byte[] data) {
        try {
            if (out == null) out = new BufferedOutputStream(Files.newOutputStream(temp,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE), 1 << 16);
            out.write(data);
            received++;
            bytes += data.length;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + temp, e);
        }
    }

    boolean complete() {
        return received >= chunks;
    }

    Path finish() {
        try {
            if (out != null) out.close();
            out = null;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot close " + temp, e);
        }
        if (bytes != size) {
            throw new IllegalStateException(name + " arrived as " + FileRoute.human(bytes)
                                            + " but was offered as " + FileRoute.human(size));
        }
        if (sha != null && !sha.isBlank() && !sha.equalsIgnoreCase(FilePaths.sha256(temp))) {
            throw new IllegalStateException(name + " failed its checksum - nothing was written to " + target);
        }
        FilePaths.place(temp, target);
        return target;
    }

    long millis() {
        return System.currentTimeMillis() - startedAt;
    }

    @Override
    public void close() {
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {
        }
        out = null;
        FilePaths.deleteQuietly(temp);
    }
}
