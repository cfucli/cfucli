package cfucli.app;

/** One exe, three things it can be. Chosen from the command line when the tray or a script starts
 *  it, and from the launcher window when a person double-clicks it. */
public enum AppRole {
    ASK, HOST, JOIN, TRAY
}
