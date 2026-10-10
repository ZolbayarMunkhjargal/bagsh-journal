package app.kinderjournal.ui;

import javafx.scene.Node;

/**
 * One top-level view in the application window.
 *
 * <p>A screen builds its node once and then rebuilds its contents whenever
 * {@link #refresh()} is called, which the shell triggers after a save or when the
 * teacher switches class.
 */
public interface Refreshable {

    /** The heading shown at the top of the content area. */
    String title();

    /** A short line under the heading; may be empty. */
    default String subtitle() {
        return "";
    }

    /** Rebuilds the screen's content. Must be safe to call repeatedly. */
    void refresh();

    /** The node currently on screen; the shell swaps in whatever this returns. */
    Node node();
}
