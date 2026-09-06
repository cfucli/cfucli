package cfucli.node;

/** How the tray reaches a window belonging to some other process.
 *  <p>
 *  Registered by the app when it runs a node in-process, absent when the node is headless. The
 *  tray asks for it rather than assuming: a node started by the cli for an agent has no window at
 *  all, and offering to show one that does not exist is how a menu starts lying. */
public interface WindowControl {

    void show();

    void hide();

    boolean visible();
}
