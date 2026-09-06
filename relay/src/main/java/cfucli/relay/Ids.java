package cfucli.relay;

import java.security.SecureRandom;

/** Session ids and one-time passwords are read aloud over a phone, so both alphabets drop the
 *  characters people mishear or mistype. The password carries 40 bits, which is what keeps an
 *  offline guess against the recorded handshake out of reach for the few hours a session lives. */
public final class Ids {

    public static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int SESSION_ID_DIGITS = 9, PASSWORD_CHARS = 8;

    static final SecureRandom RND = new SecureRandom();

    public static String newSessionId() {
        var c = new char[SESSION_ID_DIGITS];
        for (var i = 0; i < c.length; i++) c[i] = (char) ('0' + RND.nextInt(10));
        return new String(c);
    }

    public static String newPassword() {
        var c = new char[PASSWORD_CHARS];
        for (var i = 0; i < c.length; i++) c[i] = PASSWORD_ALPHABET.charAt(RND.nextInt(PASSWORD_ALPHABET.length()));
        return new String(c);
    }

    public static String prettySessionId(String id) {
        return id.length() == SESSION_ID_DIGITS ? id.substring(0, 3) + " " + id.substring(3, 6) + " " + id.substring(6) : id;
    }

    public static String prettyPassword(String pw) {
        return pw.length() == PASSWORD_CHARS ? pw.substring(0, 4) + "-" + pw.substring(4) : pw;
    }

    public static String normalise(String s) {
        return s == null ? null : s.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    public static boolean isSessionId(String s) {
        var n = normalise(s);
        return n != null && n.length() == SESSION_ID_DIGITS && n.chars().allMatch(Character::isDigit);
    }

    private Ids() {}
}
