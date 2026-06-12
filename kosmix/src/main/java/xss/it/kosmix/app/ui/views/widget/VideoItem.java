package xss.it.kosmix.app.ui.views.widget;

import javafx.scene.image.Image;
import xss.it.model.VideoInfo;

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
 * Grid model wrapper around a {@link VideoInfo} that also caches the
 * decoded thumbnail {@link Image}. The lazy grid recycles cells, so the
 * same item can be bound and rebound many times while scrolling; holding
 * the loaded image on the item means a recycled cell shows it
 * instantly and the bytes are never re-downloaded or re-decoded.
 */
public final class VideoItem {
    /**
     * The wrapped video listing entry.
     */
    private final VideoInfo info;

    /**
     * The primary (high-quality) thumbnail URL, derived from the id.
     */
    private final String thumbUrl;

    /**
     * The fallback thumbnail URL used if the primary one 404s.
     */
    private final String fallbackUrl;

    /**
     * The decoded thumbnail image once loaded; cached so recycled cells
     * reuse it. {@code null} until the first successful load.
     */
    private Image thumbnail;

    /**
     * Wraps a video listing entry and precomputes its thumbnail URLs.
     *
     * @param info the video listing entry
     */
    public VideoItem(VideoInfo info) {
        this.info = info;
        this.thumbUrl = VideoCard.jpegThumb(info.id(), "hq720");
        this.fallbackUrl = VideoCard.jpegThumb(info.id(), "hqdefault");
    }

    /**
     * Returns the wrapped video listing entry.
     *
     * @return the video info
     */
    public VideoInfo info() {
        return info;
    }

    /**
     * Returns the video id (convenience, used for de-duplication).
     *
     * @return the video id
     */
    public String id() {
        return info.id();
    }

    /**
     * Returns the primary thumbnail URL.
     *
     * @return the primary thumbnail URL
     */
    public String thumbUrl() {
        return thumbUrl;
    }

    /**
     * Returns the fallback thumbnail URL.
     *
     * @return the fallback thumbnail URL
     */
    public String fallbackUrl() {
        return fallbackUrl;
    }

    /**
     * Returns the cached decoded thumbnail, if it has loaded.
     *
     * @return the cached image, or {@code null} when not yet loaded
     */
    public Image thumbnail() {
        return thumbnail;
    }

    /**
     * Stores the decoded thumbnail so future rebinds of this item reuse
     * it without re-downloading.
     *
     * @param thumbnail the loaded image
     */
    public void thumbnail(Image thumbnail) {
        this.thumbnail = thumbnail;
    }
}
