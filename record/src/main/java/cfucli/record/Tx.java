package cfucli.record;

import module java.base;
import com.arcadedb.database.Database;
import java.util.function.Supplier;

/** ArcadeDB will not accept a write outside a transaction, and says so at runtime rather than at
 *  compile time. One place to wrap them means one place to change if batching ever becomes worth
 *  it - output is already coalesced before it gets here, so a transaction per event is cheap. */
public final class Tx {

    public static void run(Database db, Runnable work) {
        if (db.isTransactionActive()) {
            work.run();
            return;
        }
        db.transaction(work::run);
    }

    public static <T> T get(Database db, Supplier<T> work) {
        if (db.isTransactionActive()) return work.get();
        var box = new ArrayList<T>(1);
        db.transaction(() -> box.add(work.get()));
        return box.isEmpty() ? null : box.get(0);
    }

    private Tx() {}
}
