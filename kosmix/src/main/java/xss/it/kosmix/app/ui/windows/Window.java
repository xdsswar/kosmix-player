package xss.it.kosmix.app.ui.windows;

import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.ui.windows package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * The custom primary stage. skia-fx's generic
 * {@code Application<W extends Stage>} launcher constructs this class
 * reflectively (public no-arg constructor) and hands it to
 * {@code start(Window)}. {@link StageStyle#CUSTOM} removes the platform
 * title bar while keeping every OS behavior (drag, Aero snap, edge
 * resize, snap layouts) — the application paints its own chrome and
 * declares the hit regions through the Stage hit-region API.
 */
public final class Window extends Stage {
    /**
     * Creates the primary window with custom (self-drawn) decorations
     * and sensible minimum dimensions for the YouTube-like layout.
     */
    public Window() {
        super();
        /*
         * We paint our own chrome: title bar, caption buttons and the
         * settings entry all live in the scene graph.
         */
        initStyle(StageStyle.CUSTOM);
        setTitle("Kosmix");
        setMinWidth(980);
        setMinHeight(620);
    }
}
