package cfucli.relay;

import module java.base;

/** The session id and its one-time password as one string, so inviting somebody is one copy and
 *  one paste rather than two of each.
 *  <p>
 *  Two fixed-width fields make this unambiguous without a separator surviving the trip: the id is
 *  nine digits and the password is eight characters of {@link Ids#PASSWORD_ALPHABET}, so seventeen
 *  alphanumerics split at nine every time. {@link Ids#normalise} already throws away everything
 *  else, which means the dashes and spaces are decoration - they help a person read it aloud and
 *  cost nothing when it is pasted back with the formatting mangled by whatever carried it.
 *  <p>
 *  Handing both halves over together is a deliberate choice and not a shortcut. The password is
 *  what authenticates the key exchange, so it is not a second factor guarding the first; the actual
 *  gate is that the person at the far keyboard is asked to approve the viewer, and they are asked
 *  whatever the password did. Two strings to copy bought nothing and lost people halfway. */
public final class Invite {

    public static final int LENGTH = Ids.SESSION_ID_DIGITS + Ids.PASSWORD_CHARS;

    static final Pattern ID_RUN = Pattern.compile("\\b(\\d{" + Ids.SESSION_ID_DIGITS + "})\\b"),
            PW_RUN = Pattern.compile("\\b([" + Ids.PASSWORD_ALPHABET + "]{" + Ids.PASSWORD_CHARS + "})\\b");

    /** What gets copied. Grouped the way the two halves are read aloud, which is also how the
     *  window already prints them. */
    public static String format(String sessionId, String password) {
        return Ids.prettySessionId(sessionId).replace(' ', '-') + "-" + Ids.prettyPassword(password);
    }

    /** The compact form, for somewhere narrow. */
    public static String compact(String sessionId, String password) {
        return Ids.normalise(sessionId) + Ids.normalise(password);
    }

    /** Empty when the text holds no invite. Forgiving on purpose: this arrives having been through
     *  a chat client, an email, or a person retyping it, so the exact punctuation is not a promise
     *  anyone can keep. */
    public static Optional<Invited> parse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        var flat = Ids.normalise(text);
        if (flat.length() == LENGTH) {
            var id = flat.substring(0, Ids.SESSION_ID_DIGITS);
            var pw = flat.substring(Ids.SESSION_ID_DIGITS);
            if (Ids.isSessionId(id) && isPassword(pw)) return Optional.of(new Invited(id, pw));
        }
        // Not a bare invite: pull the two runs out of whatever else came with them - a pasted
        // command line, or a sentence with the id and password sitting in it.
        var upper = text.toUpperCase(Locale.ROOT);
        var id = ID_RUN.matcher(upper);
        var pw = PW_RUN.matcher(upper);
        if (id.find() && pw.find()) return Optional.of(new Invited(id.group(1), pw.group(1)));
        return Optional.empty();
    }

    public static boolean isPassword(String s) {
        if (s == null || s.length() != Ids.PASSWORD_CHARS) return false;
        return s.chars().allMatch(c -> Ids.PASSWORD_ALPHABET.indexOf(c) >= 0);
    }

    private Invite() {}
}
