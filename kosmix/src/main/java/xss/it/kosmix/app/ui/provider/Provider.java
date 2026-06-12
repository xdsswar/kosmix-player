package xss.it.kosmix.app.ui.provider;

import javafx.scene.Node;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.app.ui.Skeleton;
import xss.it.kosmix.app.ui.windows.Window;

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
 * Contract for every switchable UI surface hosted by the
 * {@link Skeleton}. A provider supplies its visual node and gains
 * access to the application context through the skeleton it belongs to.
 */
public interface Provider {
    /**
     * Returns the application context.
     *
     * @return the Context instance
     */
    Context context();

    /**
     * Returns the skeleton hosting this provider.
     *
     * @param <S> the window type managed by the skeleton
     * @return the Skeleton instance
     */
    <S extends Window> Skeleton<S> skeleton();

    /**
     * Returns the visual node representing this provider.
     *
     * @return the root node of the provider's UI
     */
    Node asNode();

    /**
     * Called when the skeleton switches away from this provider, giving
     * it a chance to release resources (media players, timelines).
     *
     * @throws Exception if cleanup fails
     */
    default void onRelease() throws Exception {
        /*
         * No-op by default; providers override when they hold resources.
         */
    }
}
