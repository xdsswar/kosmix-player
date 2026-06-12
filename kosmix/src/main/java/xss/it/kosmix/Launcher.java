package xss.it.kosmix;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.text.Font;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.app.K;
import xss.it.kosmix.app.services.DownloadService;
import xss.it.kosmix.app.services.YtService;
import xss.it.kosmix.app.ui.Skeleton;
import xss.it.kosmix.app.ui.windows.Window;
import xss.it.kosmix.helper.platform.FfmpegBootstrap;
import xss.it.kosmix.helper.platform.Platform;
import xss.it.kosmix.helper.platform.ThreadPool;
import xss.it.kosmix.helper.settings.Settings;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * The application launcher: a skia-fx {@code Application<Window>} (the
 * generic parameter makes the SDK construct the custom
 * {@link Window} as the primary stage) that also implements
 * {@link Context}, acting as the single access point for settings,
 * services, the executor and resources. {@code init()} loads the
 * Roboto fonts and bootstraps the ffmpeg runtime; {@code start()}
 * builds the {@link Skeleton} and shows the window.
 */
public class Launcher extends Application<Window> implements Context {
    /**
     * Application metadata bundle (name, version, encryption key).
     */
    private static final ResourceBundle appProperties;

    /**
     * Application display name, read from the metadata bundle.
     */
    private static final String appName;

    /**
     * Application version, read from the metadata bundle.
     */
    private static final String appVersion;

    /**
     * Persistent settings, stored under the hidden per-user folder.
     */
    private static final Settings settings;

    static {
        try {
            /*
             * Load application metadata from the resource bundle
             * 'app.properties'.
             */
            appProperties = ResourceBundle.getBundle("app");

            /*
             * Retrieve application name and version from the properties.
             */
            appName = appProperties.getString("app.name");
            appVersion = appProperties.getString("app.version");

            /*
             * Construct the hidden folder name using the lowercase
             * application name. Example: ".kosmix"
             */
            final String folder = String.format(".%s", appName.toLowerCase());

            /*
             * Construct the full path to the settings file inside the
             * per-user application data directory.
             */
            final String path = String.format(
                    "%s%s%s%s%s",
                    Platform.getProgramDataDir(),
                    Platform.dirSeparator(),
                    folder,
                    Platform.dirSeparator(),
                    "settings.cfg"
            );

            /*
             * Ensure that the application configuration directory exists.
             */
            Platform.createDirs(Platform.getProgramDataDir(), folder);

            /*
             * Initialize the Settings object with the properties file
             * path and the encryption key.
             */
            settings = new Settings(path, appProperties.getString("app.enc.key"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Shared background executor; created lazily on first use.
     */
    private ExecutorService executorService;

    /**
     * YouTube service facade; created lazily on first use.
     */
    private YtService ytService;

    /**
     * Download/mux service; created lazily on first use.
     */
    private DownloadService downloadService;

    /**
     * The primary window, assigned in {@link #start(Window)}.
     */
    private Window window;

    /**
     * Application icons applied to every window.
     */
    private List<Image> icons = List.of();

    /**
     * Required public no-arg constructor for the reflective launch.
     */
    public Launcher() {
        super();
    }

    /**
     * Pre-UI initialization: fonts, icons and the ffmpeg runtime
     * bootstrap (downloaded on first run, then advertised to the media
     * engine so WebM/Opus and the mixer work).
     *
     * @throws Exception if initialization fails fatally
     */
    @Override
    public void init() throws Exception {
        setVsyncEnabled(true);
        loadFonts();
        loadIcons();
        configureFfmpeg();
        /*
         * Apply the persisted decode strategy before any player exists.
         * AUTO is the engine default, so we only touch the API for an
         * explicit non-AUTO choice — calling it for AUTO eagerly
         * triggers a benign "nativeSetEnv not available" warning because
         * the media native isn't loaded yet.
         */
        applyDecodeMode(settings.get(K.DECODE_MODE, "AUTO"));
    }

    /**
     * Applies a decode-mode setting to the media engine, skipping the
     * call for AUTO (the engine default) to avoid an early native
     * warning. Shared by startup and the settings page.
     *
     * @param mode the decode mode name (AUTO / GPU_PREFERRED / GPU / CPU)
     */
    public static void applyDecodeMode(String mode) {
        if (mode == null || mode.isBlank() || "AUTO".equalsIgnoreCase(mode)) {
            return;
        }
        try {
            Media.setDecodeMethod(Media.DecodeMethod.valueOf(mode.toUpperCase()));
        } catch (Exception ignored) {
            /*
             * Unknown value keeps the engine default (AUTO).
             */
        }
    }

    /**
     * Builds the skeleton around the SDK-constructed custom stage and
     * shows the UI.
     *
     * @param window the primary stage (custom chrome)
     */
    @Override
    public void start(Window window) {
        this.window = window;
        final Skeleton<Window> skeleton = new Skeleton<>(this, window);
        skeleton.display();
    }

    /**
     * Shutdown hook: stops services and the executor.
     */
    @Override
    public void stop() {
        if (ytService != null) {
            ytService.close();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    /**
     * Loads the bundled Roboto family (Regular, Medium, Bold) so the
     * stylesheet can rely on it being present on any machine.
     */
    private void loadFonts() {
        final String base = "/xss/it/kosmix/assets/fonts/roboto/";
        for (String file : new String[]{
                "Roboto-Regular.ttf", "Roboto-Medium.ttf", "Roboto-Bold.ttf"}) {
            try (InputStream in = stream(base + file)) {
                if (in != null) {
                    Font.loadFont(in, 13);
                }
            } catch (Exception ignored) {
                /*
                 * Missing fonts degrade to the system default family.
                 */
            }
        }
    }

    /**
     * Loads the window icon used for the taskbar and the title-less
     * chrome.
     */
    private void loadIcons() {
        try (InputStream in = stream("/xss/it/kosmix/assets/images/icon.png")) {
            if (in != null) {
                icons = List.of(new Image(in));
            }
        } catch (Exception ignored) {
            /*
             * No icon is a cosmetic loss only.
             */
        }
    }

    /**
     * Points the media engine at the per-user ffmpeg runtime. When the
     * DLLs are already staged this is immediate; otherwise the download
     * runs on the executor and the engine is configured on completion
     * (the loader retries with the new directory automatically).
     */
    private void configureFfmpeg() {
        final Path dir = Paths.get(
                Platform.getProgramDataDir(), ".kosmix", "ffmpeg");
        if (FfmpegBootstrap.isReady(dir)) {
            Media.setFfmpegDirectory(dir.toString());
            return;
        }
        executor().execute(() -> {
            try {
                final Path ready = FfmpegBootstrap.ensure(
                        dir, msg -> System.out.println("[ffmpeg] " + msg));
                Context.update(() -> Media.setFfmpegDirectory(ready.toString()));
            } catch (Exception e) {
                System.err.println("[ffmpeg] bootstrap failed: " + e.getMessage());
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return appName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String version() {
        return appVersion;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutorService executor() {
        if (executorService == null) {
            /*
             * Pool sizing only matters for the platform-thread fallback;
             * with virtual threads each task gets its own thread.
             */
            final int cores = Platform.getCpuCores();
            final int corePoolSize = Math.clamp(cores / 2, 1, 4);
            final int maxPoolSize = Math.clamp(cores, corePoolSize, 8);
            executorService = new ThreadPool(corePoolSize, maxPoolSize, 20);
        }
        return executorService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Window window() {
        return window;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HostServices services() {
        return getHostServices();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Settings settings() {
        return settings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public YtService youtube() {
        if (ytService == null) {
            ytService = new YtService(this);
        }
        return ytService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DownloadService downloads() {
        if (downloadService == null) {
            downloadService = new DownloadService(this);
        }
        return downloadService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL load(String location) {
        return Launcher.class.getResource(location);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream stream(String location) {
        return Launcher.class.getResourceAsStream(location);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Image> icons() {
        return icons;
    }

    /**
     * Launch entry
     *
     * @param args command-line arguments
     */
    static void main(String[] args) {
        launch(Launcher.class, args);
    }
}
