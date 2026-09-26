package cfucli.relay;

/** Offline check that {@link SettingsStore} actually round-trips the newer fields - lanDirect,
 *  identityName, presenceAvailable - which it has to be told about by hand since it is a
 *  field-by-field TOML reader/writer, not reflection over {@link Settings}. A field added to the
 *  bean but not here compiles cleanly and is silently never read from disk - lanDirect was, for a
 *  while. Run with {@code mvn exec:java -Dexec.mainClass=cfucli.relay.SettingsLoadProbe
 *  -Dexec.classpathScope=test -pl relay}. */
public final class SettingsLoadProbe {

    public static void main(String[] args) {
        var home = System.getenv("CFUCLI_TEST_HOME");
        if (home != null && !home.isBlank()) System.setProperty("user.home", home);
        var s = SettingsStore.load();
        System.out.println("path=" + SettingsStore.path());
        System.out.println("identityName=" + s.identityName());
        System.out.println("presenceAvailable=" + s.presenceAvailable());
        System.out.println("lanDirect=" + s.lanDirect());
    }

    private SettingsLoadProbe() {}
}
