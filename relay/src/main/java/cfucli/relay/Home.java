package cfucli.relay;

import java.nio.file.*;

public final class Home {

    public static Path dir() {
        return ensure(Paths.get(System.getProperty("user.home"), "cfucli"));
    }

    public static Path file(String name) {
        return dir().resolve(name);
    }

    public static Path subdir(String name) {
        return ensure(dir().resolve(name));
    }

    static Path ensure(Path p) {
        try {
            Files.createDirectories(p);
            return p;
        } catch (Exception e) {
            throw new IllegalStateException("cannot create " + p, e);
        }
    }

    private Home() {}
}
