package xss.it.kosmix.app.ui.provider;

import javafx.scene.Node;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.ui.provider package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * Contract for the busy indicator overlay hosted by the skeleton.
 * Implementations decide their own visual (Kosmix uses the dual-circle
 * loader) while the skeleton only toggles visibility.
 */
public interface Loader {
    /**
     * Returns the visual node of the loader overlay.
     *
     * @return the loader node
     */
    Node asNode();

    /**
     * Shows the loader (starts its animation).
     */
    void show();

    /**
     * Hides the loader (stops its animation).
     */
    void hide();
}
