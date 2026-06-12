/**
 * Kosmix application module.
 * <p>
 * A YouTube desktop client built on the skia-fx SDK (Skia-powered
 * OpenJFX 25 fork). Uses ytnfx for data extraction, nfx-listview for
 * the lazy results grid and the kosmix.helper module for platform,
 * threading and settings infrastructure.
 */
module kosmix {
    /*
     * skia-fx SDK modules (drop-in javafx.* jars from /libs).
     */
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.media;

    /*
     * Lazy / virtualized list-grid control.
     */
    requires nfx.listview;

    /*
     * YouTube extraction library (search, details, streams, downloads).
     */
    requires ytnfx;

    /*
     * Internal infrastructure: Platform, ThreadPool, Settings, ffmpeg
     * bootstrap.
     */
    requires kosmix.helper;

    /*
     * Direct use by the application: search suggestions endpoint
     * (HTTP + JSON parsing).
     */
    requires java.net.http;
    requires com.google.gson;

    /*
     * The launcher and the custom primary stage are constructed
     * reflectively by the skia-fx Application launcher, so both
     * packages must be reachable from javafx.graphics.
     */
    exports xss.it.kosmix;
    opens xss.it.kosmix to javafx.graphics;
    exports xss.it.kosmix.app.ui.windows;
    opens xss.it.kosmix.app.ui.windows to javafx.graphics;
}
