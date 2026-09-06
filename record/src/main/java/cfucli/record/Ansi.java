package cfucli.record;

import module java.base;

/** Terminal escape sequences out, readable text in. Applied once at write time so a search never
 *  has to fight the cursor movements a shell sprays between the words. */
public final class Ansi {

    static final String ESC = "\u001B";

    static final Pattern
            OSC = Pattern.compile(Pattern.quote(ESC) + "\\][^\u0007\u001B]*(?:\u0007|" + Pattern.quote(ESC) + "\\\\)"),
            CSI = Pattern.compile(Pattern.quote(ESC) + "\\[[0-?]*[ -/]*[@-~]"),
            SS = Pattern.compile(Pattern.quote(ESC) + "[@-Z\\\\-_]"),
            CTRL = Pattern.compile("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]");

    public static String strip(String s) {
        if (s == null || s.isEmpty()) return s;
        var t = OSC.matcher(s).replaceAll("");
        t = CSI.matcher(t).replaceAll("");
        t = SS.matcher(t).replaceAll("");
        return CTRL.matcher(t).replaceAll("");
    }

    private Ansi() {}
}
