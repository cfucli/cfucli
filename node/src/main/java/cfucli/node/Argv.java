package cfucli.node;

import module java.base;

/** Splits a shell setting into a command and its arguments, so {@code --shell "cmd.exe /d"} and
 *  {@code --shell "\"C:\\Program Files\\Git\\bin\\sh.exe\" -l"} both work.
 *  <p>
 *  Splitting on whitespace alone would break the second of those, which is exactly the path a
 *  Windows shell is most likely to sit under. */
public final class Argv {

    public static String[] split(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) throw new IllegalArgumentException("no shell to start");
        var out = new ArrayList<String>();
        var token = new StringBuilder();
        var quoted = false;
        for (var c : commandLine.trim().toCharArray()) {
            if (c == '"') quoted = !quoted;
            else if (!quoted && Character.isWhitespace(c)) {
                if (token.length() > 0) out.add(token.toString());
                token.setLength(0);
            } else token.append(c);
        }
        if (token.length() > 0) out.add(token.toString());
        return out.toArray(String[]::new);
    }

    private Argv() {}
}
