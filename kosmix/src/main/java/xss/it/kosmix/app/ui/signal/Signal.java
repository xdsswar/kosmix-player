package xss.it.kosmix.app.ui.signal;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.ui.signal package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * Lightweight inter-page messages. Pages receive signals through
 * {@code Page.signal(Signal)} without the sender needing to know the
 * concrete page type.
 */
public enum Signal {
    /**
     * No-op signal; the default state of every page.
     */
    IGNORE,

    /**
     * Asks the receiving page to refresh its content.
     */
    REFRESH,

    /**
     * Tells the receiving page that playback should stop (e.g. the
     * window is closing or another page takes over the media output).
     */
    STOP_PLAYBACK
}
