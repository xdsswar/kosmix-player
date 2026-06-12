package xss.it.kosmix.app.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.util.Duration;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.app.K;
import xss.it.kosmix.app.ui.provider.Provider;
import xss.it.kosmix.app.ui.views.HomeProvider;
import xss.it.kosmix.app.ui.views.pages.PlayerPage;
import xss.it.kosmix.app.ui.views.pages.SettingsPage;
import xss.it.kosmix.app.ui.views.widget.DownloadsPanel;
import xss.it.kosmix.app.ui.views.widget.KosmixDualLoader;
import xss.it.kosmix.app.ui.views.widget.TitleBar;
import xss.it.kosmix.app.ui.windows.Window;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.ui package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * The scene root and orchestrator of the whole UI: hosts the custom
 * title bar, switches {@link Provider} views with a YouTube-like
 * fade/slide transition, owns the busy-loader overlay, registers the
 * native hit regions of the custom chrome and persists/restores the
 * window geometry through the settings.
 */
public final class Skeleton<T extends Window> extends AnchorPane {
    /**
     * The application {@link Context} associated with this skeleton.
     */
    private final Context context;

    /**
     * The window instance managed by this skeleton.
     */
    private final T window;

    /**
     * The {@link Scene} representing this skeleton's root UI.
     */
    private final Scene scene;

    /**
     * The custom window chrome (brand, search, gear, caption buttons).
     */
    private final TitleBar titleBar;

    /**
     * Container the active provider's node lives in.
     */
    private final AnchorPane contentHolder;

    /**
     * Translucent overlay hosting the dual-circle loader.
     */
    private final StackPane loaderOverlay;

    /**
     * The dual-circle busy indicator.
     */
    private final KosmixDualLoader loader;

    /**
     * The home grid — kept alive across navigation so search results
     * and scroll position survive a round trip into the player.
     */
    private HomeProvider home;

    /**
     * Back history: snapshots able to recreate previously visited
     * pages (pages dispose their resources when left, so instances
     * are not kept — only the recipe to rebuild them).
     */
    private final Deque<Supplier<Provider>> backStack = new ArrayDeque<>();

    /**
     * Forward history, populated by {@link #back()} and cleared by any
     * fresh navigation.
     */
    private final Deque<Supplier<Provider>> forwardStack = new ArrayDeque<>();

    /**
     * Observable "can navigate back" state for the chrome buttons.
     */
    private final BooleanProperty canBack = new SimpleBooleanProperty(this, "canBack", false);

    /**
     * Observable "can navigate forward" state for the chrome buttons.
     */
    private final BooleanProperty canForward = new SimpleBooleanProperty(this, "canForward", false);

    /**
     * {@code true} while a back/forward jump applies its provider, so
     * the history listener does not record the jump as a fresh visit.
     */
    private boolean navigatingHistory;

    /**
     * Path or reference to the global application stylesheet.
     */
    private static String styleSheet = null;

    /**
     * Creates a new {@code Skeleton} bound to the given {@link Context}
     * and window.
     *
     * @param context the application context
     * @param window  the window instance to be managed
     */
    public Skeleton(Context context, T window) {
        super();
        this.context = context;
        this.window = window;
        this.scene = new Scene(this, 1280, 800);
        styleSheet = context.load("/xss/it/kosmix/css/style.css").toExternalForm();

        this.titleBar = new TitleBar(context);
        this.contentHolder = new AnchorPane();
        this.loader = new KosmixDualLoader();
        this.loaderOverlay = new StackPane(loader);

        /*
         * Initialize
         */
        initialize();
    }

    /**
     * Builds the static layout, wires the chrome callbacks, registers
     * the hit regions and installs the initial provider.
     */
    private void initialize() {
        getStyleClass().add("skeleton");
        if (styleSheet != null) {
            getStylesheets().add(styleSheet);
        }

        /*
         * Title bar pinned to the top, content right below it, loader
         * overlay across the content area.
         */
        AnchorPane.setTopAnchor(titleBar, 0d);
        AnchorPane.setLeftAnchor(titleBar, 0d);
        AnchorPane.setRightAnchor(titleBar, 0d);

        contentHolder.getStyleClass().add("content-holder");
        anchorContent(TitleBar.HEIGHT);

        loaderOverlay.getStyleClass().add("loader-overlay");
        AnchorPane.setTopAnchor(loaderOverlay, TitleBar.HEIGHT);
        AnchorPane.setLeftAnchor(loaderOverlay, 0d);
        AnchorPane.setRightAnchor(loaderOverlay, 0d);
        AnchorPane.setBottomAnchor(loaderOverlay, 0d);
        loaderOverlay.setVisible(false);

        /*
         * Global download toasts pinned to the bottom-right corner,
         * above every page.
         */
        final DownloadsPanel downloadsPanel = new DownloadsPanel(context);
        AnchorPane.setRightAnchor(downloadsPanel, 18d);
        AnchorPane.setBottomAnchor(downloadsPanel, 18d);

        getChildren().addAll(contentHolder, titleBar, loaderOverlay, downloadsPanel);

        /*
         * Chrome callbacks: search routes into the home grid, the gear
         * opens settings, the brand returns home.
         */
        home = new HomeProvider(this);
        titleBar.setOnSearch(query -> {
            /*
             * Searching while a video plays must not interrupt it: the
             * player keeps playing and only its side list refreshes
             * with the new results. Anywhere else, search lands on the
             * home grid.
             */
            if (getProvider() instanceof PlayerPage player) {
                player.updateList(query);
                return;
            }
            if (getProvider() != home) {
                setProvider(home);
            }
            home.search(query);
        });
        titleBar.wireHistory(canBack, canForward, this::back, this::forward);
        titleBar.setOnSettings(() -> {
            if (!(getProvider() instanceof SettingsPage)) {
                setProvider(new SettingsPage(this));
            }
        });
        titleBar.setOnHome(() -> {
            if (getProvider() != home) {
                setProvider(home);
            }
        });

        /*
         * Native hit regions for the custom chrome: the OS handles
         * drag, double-click maximize, Aero snap and the Win11 snap
         * layout flyout; we only provide the geometry.
         */
        window.setCaptionRegions(titleBar.captionRegions());
        window.setMinRegion(titleBar.minRegion());
        window.setMaxRegion(titleBar.maxRegion());
        window.setCloseRegion(titleBar.closeRegion());

        /*
         * Full-screen playback hides the chrome entirely.
         */
        window.fullScreenProperty().addListener((obs, was, full) -> {
            titleBar.setVisible(!full);
            titleBar.setManaged(!full);
            anchorContent(full ? 0 : TitleBar.HEIGHT);
        });

        /*
         * Switch provider with a transition whenever the property
         * flips, recording fresh navigations into the back history
         * (back/forward jumps set the navigatingHistory flag and are
         * not re-recorded).
         */
        providerProperty().addListener((obs, old, provider) -> {
            if (!navigatingHistory && old != null) {
                backStack.push(snapshot(old));
                forwardStack.clear();
            }
            canBack.set(!backStack.isEmpty());
            canForward.set(!forwardStack.isEmpty());
            loadProvider(provider, old);
        });

        /*
         * Persist geometry on close.
         */
        window.setOnCloseRequest(event -> closeCallback());

        /*
         * Land on the home grid.
         */
        setProvider(home);
    }

    /**
     * Applies the content holder's top anchor (chrome height or zero in
     * full-screen).
     *
     * @param top the top inset
     */
    private void anchorContent(double top) {
        AnchorPane.setTopAnchor(contentHolder, top);
        AnchorPane.setLeftAnchor(contentHolder, 0d);
        AnchorPane.setRightAnchor(contentHolder, 0d);
        AnchorPane.setBottomAnchor(contentHolder, 0d);
        AnchorPane.setTopAnchor(loaderOverlay, top);
    }

    /**
     * The provider associated with this skeleton.
     * <p>
     * This property holds the active {@link Provider} and is observed
     * to drive the page-switch transition.
     */
    private ObjectProperty<Provider> provider;

    /**
     * Returns the current {@link Provider} value.
     *
     * @return the active provider, or {@code null} if none is set
     */
    public Provider getProvider() {
        return providerProperty().get();
    }

    /**
     * Returns the {@link ObjectProperty} wrapper for the provider.
     *
     * @return the {@code ObjectProperty} for the provider
     */
    public ObjectProperty<Provider> providerProperty() {
        if (provider == null) {
            provider = new SimpleObjectProperty<>(this, "provider", null);
        }
        return provider;
    }

    /**
     * Sets the {@link Provider} value for this skeleton.
     *
     * @param provider the provider to display
     */
    public void setProvider(Provider provider) {
        providerProperty().set(provider);
    }

    /**
     * Swaps the visible provider with a fade + slide transition (the
     * YouTube page-change feel) and releases the previous provider once
     * it is off screen.
     *
     * @param provider the incoming provider
     * @param old      the outgoing provider, may be {@code null}
     */
    private void loadProvider(Provider provider, Provider old) {
        if (provider == null) {
            return;
        }
        final Node node = provider.asNode();
        AnchorPane.setTopAnchor(node, 0d);
        AnchorPane.setLeftAnchor(node, 0d);
        AnchorPane.setRightAnchor(node, 0d);
        AnchorPane.setBottomAnchor(node, 0d);

        node.setOpacity(0);
        node.setTranslateY(16);
        contentHolder.getChildren().add(node);

        final FadeTransition fade = new FadeTransition(Duration.millis(240), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        final TranslateTransition slide = new TranslateTransition(Duration.millis(240), node);
        slide.setFromY(16);
        slide.setToY(0);

        final ParallelTransition enter = new ParallelTransition(fade, slide);
        enter.setOnFinished(e -> {
            if (old != null) {
                contentHolder.getChildren().remove(old.asNode());
                try {
                    old.onRelease();
                } catch (Exception ignored) {
                    /*
                     * Releasing a page must never break navigation.
                     */
                }
            }
        });
        enter.play();
    }

    /**
     * Builds a history snapshot able to recreate the given provider.
     * Pages release their resources (media players, scene hooks) when
     * left, so the history stores recipes instead of instances; only
     * the home grid survives as a singleton.
     *
     * @param provider the provider to snapshot
     * @return a supplier recreating an equivalent provider
     */
    private Supplier<Provider> snapshot(Provider provider) {
        if (provider instanceof PlayerPage player) {
            final var video = player.video();
            return () -> new PlayerPage(this, video);
        }
        if (provider instanceof SettingsPage) {
            return () -> new SettingsPage(this);
        }
        return () -> home;
    }

    /**
     * Navigates one step back in the history.
     */
    public void back() {
        if (backStack.isEmpty()) {
            return;
        }
        forwardStack.push(snapshot(getProvider()));
        navigatingHistory = true;
        setProvider(backStack.pop().get());
        navigatingHistory = false;
        canBack.set(!backStack.isEmpty());
        canForward.set(!forwardStack.isEmpty());
    }

    /**
     * Navigates one step forward in the history.
     */
    public void forward() {
        if (forwardStack.isEmpty()) {
            return;
        }
        backStack.push(snapshot(getProvider()));
        navigatingHistory = true;
        setProvider(forwardStack.pop().get());
        navigatingHistory = false;
        canBack.set(!backStack.isEmpty());
        canForward.set(!forwardStack.isEmpty());
    }

    /**
     * Shows the loader overlay (with its pulse animation running).
     */
    public void showLoader() {
        loader.start();
        loader.setVisible(true);
        loaderOverlay.setVisible(true);
    }

    /**
     * Hides the loader overlay and stops its animation.
     */
    public void hideLoader() {
        loader.stop();
        loaderOverlay.setVisible(false);
    }

    /**
     * Returns the application context.
     *
     * @return the Context instance
     */
    public Context context() {
        return context;
    }

    /**
     * Returns the managed window.
     *
     * @return the window instance
     */
    public T window() {
        return window;
    }

    /**
     * Returns the title bar widget.
     *
     * @return the chrome widget
     */
    public TitleBar titleBar() {
        return titleBar;
    }

    /**
     * Returns the persistent home grid instance.
     *
     * @return the home provider
     */
    public HomeProvider home() {
        return home;
    }

    /**
     * Applies the scene, restores the persisted window geometry and
     * shows the window.
     */
    public void display() {
        window.setScene(scene);
        window.getIcons().setAll(context.icons());
        restoreGeometry();
        window.show();
    }

    /**
     * Restores the persisted geometry when it is still visible on one
     * of the current screens; falls back to centering otherwise.
     */
    private void restoreGeometry() {
        final double w = context.settings().getDouble(K.WIN_W, 1280);
        final double h = context.settings().getDouble(K.WIN_H, 800);
        final double x = context.settings().getDouble(K.WIN_X, Double.NaN);
        final double y = context.settings().getDouble(K.WIN_Y, Double.NaN);

        window.setWidth(Math.max(window.getMinWidth(), w));
        window.setHeight(Math.max(window.getMinHeight(), h));

        boolean placed = false;
        if (!Double.isNaN(x) && !Double.isNaN(y)) {
            /*
             * Only restore a position that still intersects a screen —
             * monitors may have been unplugged since the last session.
             */
            final Rectangle2D probe = new Rectangle2D(x, y, Math.max(64, w), Math.max(64, h));
            for (Screen screen : Screen.getScreens()) {
                if (screen.getVisualBounds().intersects(probe)) {
                    window.setX(x);
                    window.setY(y);
                    placed = true;
                    break;
                }
            }
        }
        if (!placed) {
            window.centerOnScreen();
        }
        if (context.settings().getBoolean(K.WIN_MAX, false)) {
            window.setMaximized(true);
        }
    }

    /**
     * Persists the window geometry and lets the active page stop any
     * running playback before the window goes away.
     */
    private void closeCallback() {
        final var settings = context.settings();
        settings.setBoolean(K.WIN_MAX, window.isMaximized());
        if (!window.isMaximized()) {
            settings.setDouble(K.WIN_X, window.getX());
            settings.setDouble(K.WIN_Y, window.getY());
            settings.setDouble(K.WIN_W, window.getWidth());
            settings.setDouble(K.WIN_H, window.getHeight());
        }
        final Provider active = getProvider();
        if (active != null) {
            try {
                active.onRelease();
            } catch (Exception ignored) {
                /*
                 * Best effort cleanup on shutdown.
                 */
            }
        }
    }
}
