package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

/** The manual, which lives in a resource rather than in the source because it is prose and it is
 *  long. Printed by this verb and pointed at from every other -help. */
@Command(name = "guide", description = "The manual: what this tool is, how the pieces fit, and every verb with its flags.")
public final class GuideCmd implements Callable<Integer> {

    public static final String RESOURCE = "manual.txt";

    @Override
    public Integer call() {
        System.out.print(text());
        return 0;
    }

    public static String text() {
        try (var in = GuideCmd.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("the manual is missing from this jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
