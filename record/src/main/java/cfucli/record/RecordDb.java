package cfucli.record;

import module java.base;
import module java.logging;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import cfucli.relay.Home;
import datapotter.arcadedbhelper.InitDoc;
import datapotter.arcadedbhelper.TypeDef;

/** The embedded recording store, through DatabaseFactory rather than ArcadeDBServer: no HTTP port
 *  to collide with, no startup banner for an agent to parse.
 *  <p>
 *  ArcadeDB takes an exclusive lock on its directory, so only the node daemon may hold this. That
 *  is the reason the cli talks to the node over loopback instead of opening the store itself. */
public final class RecordDb {

    static {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        Logger.getLogger("com.arcadedb").setLevel(Level.WARNING);
        Logger.getLogger("com.arcadedb.script.GraalPolyglotEngine").setLevel(Level.OFF);
    }

    public static final String DB_NAME = "sessions", DEFAULT_NODE = "default";

    public static final List<TypeDef<?>> TYPES = List.of(SessionRow.TYPEDEF, EventRow.TYPEDEF, JobRow.TYPEDEF);

    static RecordDb instance;

    final DatabaseFactory factory;
    final Database db;
    final Path location;

    private RecordDb(String node) {
        location = Home.subdir("db").resolve(DEFAULT_NODE.equals(node) ? DB_NAME : DB_NAME + "-" + node);
        factory = new DatabaseFactory(location.toString());
        db = factory.exists() ? factory.open() : factory.create();
        InitDoc.initDocTypes(db, TYPES.toArray(TypeDef[]::new));
    }

    public static synchronized RecordDb get() {
        return open(DEFAULT_NODE);
    }

    /** One store per node name. Two node processes on one machine - which is exactly how the two
     *  ends are tried out locally - cannot share one, because ArcadeDB locks its directory. */
    public static synchronized RecordDb open(String node) {
        if (instance == null) instance = new RecordDb(node == null ? DEFAULT_NODE : node);
        return instance;
    }

    public static synchronized boolean isOpen() {
        return instance != null;
    }

    public Database db() {
        return db;
    }

    public Path location() {
        return location;
    }

    public static synchronized void shutdown() {
        var it = instance;
        instance = null;
        if (it == null) return;
        try {
            if (it.db.isOpen()) it.db.close();
        } finally {
            it.factory.close();
        }
    }
}
