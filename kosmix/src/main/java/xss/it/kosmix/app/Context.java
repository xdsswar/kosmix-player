package xss.it.kosmix.app;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.image.Image;
import xss.it.kosmix.app.services.DownloadService;
import xss.it.kosmix.app.services.YtService;
import xss.it.kosmix.app.ui.windows.Window;
import xss.it.kosmix.helper.settings.Settings;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;

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
 * The Context interface provides access to the primary application
 * components and utilities: the main window, the shared executor, the
 * persistent settings, the YouTube and download services and resource
 * loading helpers. It also includes a convenience method for safely
 * updating the UI from background threads.
 */
public interface Context {
    /**
     * Returns the application name.
     *
     * @return the name of the application
     */
    String name();

    /**
     * Returns the application version.
     *
     * @return the version string
     */
    String version();

    /**
     * Provides the shared {@link ExecutorService} for running background
     * or asynchronous tasks (ytnfx calls, downloads, bootstraps).
     *
     * @return the executor service instance
     */
    ExecutorService executor();

    /**
     * Returns the primary application window.
     *
     * @return the main Window instance
     */
    Window window();

    /**
     * Provides access to platform-specific services such as opening web
     * pages or local files in the system default handler.
     *
     * @return the HostServices instance
     */
    HostServices services();

    /**
     * Retrieves the persistent application settings.
     *
     * @return the Settings object
     */
    Settings settings();

    /**
     * Returns the YouTube service: asynchronous search, video details
     * and stream-format selection on top of ytnfx.
     *
     * @return the YtService instance
     */
    YtService youtube();

    /**
     * Returns the download service: stream downloads with progress and
     * audio/video muxing into a single MP4.
     *
     * @return the DownloadService instance
     */
    DownloadService downloads();

    /**
     * Schedules the specified task to run on the FX Application Thread.
     *
     * @param rn the Runnable task to be executed
     */
    static void update(Runnable rn) {
        if (Platform.isFxApplicationThread()) {
            rn.run();
        } else {
            Platform.runLater(rn);
        }
    }

    /**
     * Resolves a classpath resource at the given location into a
     * {@link URL}.
     *
     * @param location the absolute resource path (e.g.
     *                 {@code /xss/it/kosmix/css/style.css})
     * @return the resolved URL, or {@code null} if not found
     */
    URL load(String location);

    /**
     * Opens the specified classpath resource as an {@link InputStream}.
     *
     * @param location the absolute resource path
     * @return an InputStream for the resource, or {@code null} if not found
     */
    InputStream stream(String location);

    /**
     * Returns the list of application icons applied to every window.
     *
     * @return an immutable list of icons; empty if none are set
     */
    List<Image> icons();

    /**
     * Returns the external form of the global stylesheet, for nodes
     * that live outside the scene graph (popups do not inherit the
     * scene's stylesheets and must add it themselves).
     *
     * @return the stylesheet URL string
     */
    default String stylesheet() {
        return load("/xss/it/kosmix/css/style.css").toExternalForm();
    }
}
