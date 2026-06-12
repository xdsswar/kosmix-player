package xss.it.kosmix.app;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * Central registry of settings keys and a handful of application-wide
 * constants. Keeping every key here prevents typo-divergence between
 * writers and readers of the settings file.
 */
public final class K {
    /**
     * Private constructor. This is a constants holder and must never be
     * instantiated.
     */
    private K() {
        throw new AssertionError("No instances of K");
    }

    /**
     * ytnfx client key used for extraction (e.g. {@code web_safari},
     * {@code android_vr}, {@code ios}).
     */
    public static final String CLIENT = "yt.client";

    /**
     * Custom User-Agent applied to the selected client and to media
     * playback requests. Blank means "use the client default".
     */
    public static final String USER_AGENT = "yt.user.agent";

    /**
     * Directory where finished downloads (muxed MP4s) are placed.
     */
    public static final String DOWNLOAD_DIR = "download.dir";

    /**
     * Query used to pre-populate the home grid on startup.
     */
    public static final String HOME_QUERY = "home.query";

    /**
     * Persisted player volume in the {@code 0..1} range.
     */
    public static final String VOLUME = "player.volume";

    /**
     * Persisted window geometry: x position.
     */
    public static final String WIN_X = "win.x";

    /**
     * Persisted window geometry: y position.
     */
    public static final String WIN_Y = "win.y";

    /**
     * Persisted window geometry: width.
     */
    public static final String WIN_W = "win.w";

    /**
     * Persisted window geometry: height.
     */
    public static final String WIN_H = "win.h";

    /**
     * Persisted window geometry: maximized flag.
     */
    public static final String WIN_MAX = "win.max";

    /**
     * Playback quality mode. One of {@link #QUALITY_AUTO},
     * {@link #QUALITY_BEST}, or a numeric height ("1080", "720", ...).
     */
    public static final String QUALITY = "player.quality";

    /**
     * Quality mode: pick the best resolution the measured network can
     * sustain (the default).
     */
    public static final String QUALITY_AUTO = "auto";

    /**
     * Quality mode: always use the highest available resolution.
     */
    public static final String QUALITY_BEST = "best";

    /**
     * Default quality mode — 720p out of the box (a good balance of
     * quality and bandwidth); the user can switch to Auto/Best/other in
     * Settings or the in-player quality menu.
     */
    public static final String DEFAULT_QUALITY = "720";

    /**
     * Decode strategy for the media engine: AUTO, GPU_PREFERRED, GPU
     * or CPU.
     */
    public static final String DECODE_MODE = "player.decode";

    /**
     * Whether the player automatically continues with the first side
     * list entry when a video ends.
     */
    public static final String AUTOPLAY = "player.autoplay";

    /**
     * Maximum number of downloads running at the same time.
     */
    public static final String MAX_DOWNLOADS = "download.max.parallel";

    /**
     * Maximum number of mux operations running at the same time.
     */
    public static final String MAX_MIXES = "download.max.mixes";

    /**
     * Parallel HTTP connections used per stream download.
     */
    public static final String DL_CONNECTIONS = "download.connections";

    /**
     * Network timeout for downloads/bootstraps, in seconds.
     */
    public static final String NET_TIMEOUT = "net.timeout";

    /**
     * Sentinel client value meaning "use ytnfx's default client chain"
     * (web_safari → android_vr) — the chain that yields the complete
     * set of high-resolution adaptive formats.
     */
    public static final String AUTO_CLIENT = "auto";

    /**
     * Default ytnfx client when none is configured.
     */
    public static final String DEFAULT_CLIENT = AUTO_CLIENT;

    /**
     * Default query for the home grid.
     */
    public static final String DEFAULT_HOME_QUERY = "music videos";
}
