package xss.it.kosmix.app.ui.views.widget;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.SvgImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
 * A 16:9 thumbnail surface with asynchronous image loading and rounded
 * corners. The image is loaded with background loading enabled so list
 * cells never block the FX thread; while loading (or on error) a flat
 * placeholder is shown. Designed for recycled cells: {@link #setUrl}
 * cancels nothing but simply swaps the displayed image when the new
 * load completes and still matches the latest request.
 */
public final class AsyncThumb extends StackPane {
    /**
     * Session-wide LRU of loaded images keyed by URL, so recycled
     * cells never re-download a thumbnail they already fetched.
     */
    private static final Map<String, Image> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                /**
                 * Evicts the eldest image past the cache capacity.
                 *
                 * @param eldest the eldest cache entry
                 * @return {@code true} when the entry must be removed
                 */
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > 220;
                }
            });

    /**
     * The image view actually painting the thumbnail.
     */
    private final ImageView view;

    /**
     * Placeholder glyph shown on the flat surface until the image is
     * available (and again when a recycled cell clears its image).
     */
    private final SvgImageView placeholder;

    /**
     * Rounded-rectangle clip giving the YouTube-like 12px corners.
     */
    private final Rectangle clip;

    /**
     * The most recently requested URL; guards against out-of-order
     * completions when cells are recycled quickly while scrolling.
     */
    private String currentUrl;

    /**
     * Creates the thumbnail surface.
     */
    public AsyncThumb() {
        super();
        getStyleClass().add("async-thumb");
        setMinSize(0, 0);

        /*
         * Soft play glyph as the loading placeholder; it sits behind
         * the image view and toggles with the image's presence.
         */
        placeholder = Icons.icon("play", 36, Color.web("#c9c9c9"));

        view = new ImageView();
        view.setPreserveRatio(false);
        view.setSmooth(true);
        view.imageProperty().addListener((obs, o, image) ->
                placeholder.setVisible(image == null));
        getChildren().addAll(placeholder, view);

        /*
         * The clip tracks the widget size so corners stay rounded at
         * any cell width.
         */
        clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        /*
         * The image stretches to fill the surface; the surface itself
         * is kept at 16:9 by the owning layout.
         */
        view.fitWidthProperty().bind(widthProperty());
        view.fitHeightProperty().bind(heightProperty());
        setPrefHeight(Region.USE_COMPUTED_SIZE);
    }

    /**
     * Sets the corner radius of the rounded clip.
     *
     * @param radius the arc radius in pixels
     */
    public void setCornerRadius(double radius) {
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
    }

    /**
     * Binds the thumbnail to a grid item, reusing the item's already
     * decoded image when present (recycled cell, no re-download) and
     * otherwise loading it and caching the result back on the item.
     *
     * @param item the grid item, or {@code null} to clear
     */
    public void setItem(VideoItem item) {
        if (item == null) {
            currentUrl = null;
            view.setImage(null);
            return;
        }
        /*
         * Fast path: the item already carries a decoded image from a
         * previous binding — show it immediately, no network at all.
         */
        final Image cached = item.thumbnail();
        if (cached != null && !cached.isError()) {
            currentUrl = item.thumbUrl();
            view.setImage(cached);
            return;
        }
        currentUrl = item.thumbUrl();
        load(item.thumbUrl(), item.fallbackUrl(), item);
    }

    /**
     * Requests the given image URL, loading it in the background and
     * displaying it once ready (if still the latest request).
     *
     * @param url the image URL, or {@code null} to clear
     */
    public void setUrl(String url) {
        setUrl(url, null);
    }

    /**
     * Requests the given image URL with an optional fallback that is
     * tried when the primary fails (e.g. a resolution variant that
     * does not exist for every video).
     *
     * @param url      the primary image URL, or {@code null} to clear
     * @param fallback the fallback URL, may be {@code null}
     */
    public void setUrl(String url, String fallback) {
        currentUrl = url;
        if (url == null || url.isBlank()) {
            view.setImage(null);
            return;
        }
        load(url, fallback, null);
    }

    /**
     * Shared load path: serve from the session image cache, display on
     * completion when this widget still wants the URL, fall back on
     * error, and (when an item is supplied) cache the decoded image
     * back onto it so recycled cells reuse it.
     *
     * @param url      the image URL to load
     * @param fallback the fallback URL on error, may be {@code null}
     * @param item     the grid item to cache the result on, or
     *                 {@code null} for plain URL loads
     */
    private void load(String url, String fallback, VideoItem item) {
        /*
         * Background loading keeps cell updates non-blocking; the
         * session cache makes recycled cells reuse already-fetched
         * images even across different items sharing a URL.
         */
        final Image image = CACHE.computeIfAbsent(url, u -> new Image(u, true));
        if (image.isError()) {
            CACHE.remove(url);
            tryFallback(url, fallback, item);
            return;
        }
        if (image.getProgress() >= 1.0) {
            view.setImage(image);
            if (item != null) {
                item.thumbnail(image);
            }
            return;
        }
        view.setImage(image);
        image.progressProperty().addListener((obs, o, p) -> {
            if (p.doubleValue() >= 1.0 && !image.isError()) {
                if (item != null) {
                    item.thumbnail(image);
                }
                if (Objects.equals(currentUrl, url)) {
                    view.setImage(image);
                }
            }
        });
        image.errorProperty().addListener((obs, o, failed) -> {
            if (failed) {
                CACHE.remove(url);
                if (Objects.equals(currentUrl, url)) {
                    tryFallback(url, fallback, item);
                }
            }
        });
    }

    /**
     * Loads the fallback variant after a primary failure.
     *
     * @param failed   the URL that failed
     * @param fallback the fallback URL, may be {@code null}
     * @param item     the grid item to cache the result on, or null
     */
    private void tryFallback(String failed, String fallback, VideoItem item) {
        if (fallback != null && !fallback.equals(failed)) {
            currentUrl = fallback;
            load(fallback, null, item);
        } else {
            view.setImage(null);
        }
    }

    /**
     * Returns the internal image view (for advanced consumers such as
     * viewport-based cropping).
     *
     * @return the backing ImageView
     */
    public ImageView view() {
        return view;
    }
}
