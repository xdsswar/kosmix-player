package xss.it.kosmix.app.ui.views.widget;

import javafx.scene.image.SvgImage;
import javafx.scene.image.SvgImageView;
import javafx.scene.paint.Color;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.ui.views.widget package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * Factory for the application's SVG icons. Parses each SVG document
 * once into a shared {@link SvgImage} (vector data, DPI-independent)
 * and hands out cheap {@link SvgImageView} instances sized and tinted
 * per call. Icon names map 1:1 to the files under
 * {@code /xss/it/kosmix/assets/svg/}.
 */
public final class Icons {
    /**
     * Base classpath folder containing the SVG assets.
     */
    private static final String BASE = "/xss/it/kosmix/assets/svg/";

    /**
     * One parsed {@link SvgImage} per icon name; shared across all views.
     */
    private static final Map<String, SvgImage> CACHE = new ConcurrentHashMap<>();

    /**
     * Private constructor. This is a static factory and must never be
     * instantiated.
     */
    private Icons() {
        throw new AssertionError("No instances of Icons");
    }

    /**
     * Creates a square icon view for the given asset.
     *
     * @param name the icon file name without extension (e.g. {@code play})
     * @param size the desired width/height in logical pixels
     * @param tint the recolor tint, or {@code null} to keep authored colors
     * @return a ready-to-place SvgImageView
     */
    public static SvgImageView icon(String name, double size, Color tint) {
        final SvgImage image = CACHE.computeIfAbsent(name, n -> {
            final URL url = Icons.class.getResource(BASE + n + ".svg");
            if (url == null) {
                throw new IllegalArgumentException("Missing SVG asset: " + n);
            }
            return new SvgImage(url.toExternalForm());
        });
        final SvgImageView view = new SvgImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        if (tint != null) {
            /*
             * SRC_IN replaces every painted pixel's color while keeping
             * the alpha — exactly what monochrome glyphs need.
             */
            view.setTint(tint);
            view.setTintMode(SvgImageView.TintMode.SRC_IN);
        }
        return view;
    }

    /**
     * Creates a square icon view in the default chrome ink color.
     *
     * @param name the icon file name without extension
     * @param size the desired width/height in logical pixels
     * @return a ready-to-place SvgImageView
     */
    public static SvgImageView icon(String name, double size) {
        return icon(name, size, Color.web("#0f0f0f"));
    }
}
