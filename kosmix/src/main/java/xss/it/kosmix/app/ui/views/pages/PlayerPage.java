package xss.it.kosmix.app.ui.views.pages;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.util.Duration;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.app.K;
import xss.it.kosmix.app.services.DownloadService;
import xss.it.kosmix.app.services.YtService;
import xss.it.kosmix.app.ui.Skeleton;
import xss.it.kosmix.app.ui.provider.AbstractProvider;
import xss.it.kosmix.app.ui.provider.Page;
import xss.it.kosmix.app.ui.signal.Signal;
import xss.it.kosmix.app.ui.views.widget.AsyncThumb;
import xss.it.kosmix.app.ui.views.widget.Icons;
import xss.it.kosmix.app.ui.views.widget.KosmixDualLoader;
import xss.it.kosmix.app.ui.views.widget.VideoCard;
import xss.it.kosmix.helper.utils.Strings;
import xss.it.model.Format;
import xss.it.model.VideoDetails;
import xss.it.model.VideoInfo;

import java.util.*;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.ui.views.pages package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * The watch page, modeled after the YouTube web layout: the video
 * surface with an auto-hiding control bar (play/pause, seek slider
 * with a storyboard hover preview, time, volume slider with a dynamic
 * icon, download with progress, fullscreen), the title / metadata /
 * expandable description below, and a related-videos rail on the
 * right. Playback uses skia-fx's dual-source engine — the best
 * video-only and audio-only streams play as one synchronized player.
 */
public final class PlayerPage extends AbstractProvider implements Page {
    /**
     * Width of the related-videos rail.
     */
    private static final double RELATED_WIDTH = 384;

    /**
     * Seek de-duplication tolerance (one drag gesture fires twice).
     */
    private static final double SEEK_DEDUP_MS = 200.0;

    /**
     * Seek de-duplication time window in nanoseconds.
     */
    private static final long SEEK_DEDUP_WINDOW_NS = 300_000_000L;

    /**
     * The video this page plays.
     */
    private final VideoInfo video;

    /**
     * Resolved details (formats, storyboards, description); set once
     * the lookup completes.
     */
    private VideoDetails details;

    /**
     * The active player; recreated never — the page is per-video.
     */
    private MediaPlayer player;

    /**
     * The video output node.
     */
    private final MediaView mediaView;

    /**
     * The black video surface stack (video + controls + loader).
     */
    private final StackPane surface;

    /**
     * Auto-hiding control bar at the bottom of the surface.
     */
    private final VBox controls;

    /**
     * Busy indicator shown while the streams are being resolved.
     */
    private final KosmixDualLoader loader;

    /**
     * Branded overlay (app icon + "Kosmix Player" + loader) shown on
     * the surface until playback is ready, with a soft glow and pulse.
     */
    private final VBox brandOverlay;

    /**
     * Per-page quality override chosen from the player menu: height
     * cap in pixels, 0 = best available, {@code null} = follow the
     * global quality setting (Auto by default).
     */
    private Integer qualityOverride;

    /**
     * Playback position to restore after a quality switch.
     */
    private Duration resumeAt;

    /**
     * The vertical list of quality rows — an overlay shown inside the
     * player surface (anchored bottom-right above the control bar),
     * not a detached popup window.
     */
    private final VBox qualityList;

    /**
     * The gear button that toggles the quality menu (exempt from the
     * click-outside auto-dismiss).
     */
    private StackPane qualityBtn;

    /**
     * In-surface menu to pick the download quality before starting a
     * download.
     */
    private final VBox downloadMenu;

    /**
     * The download button that toggles the download-quality menu.
     */
    private StackPane downloadBtn;

    /**
     * Error line shown on the surface when something fails.
     */
    private final Label errorLabel;

    /**
     * Play / pause toggle button glyph holder.
     */
    private final StackPane playGlyph;

    /**
     * Seek slider (milliseconds).
     */
    private final Slider seekSlider;

    /**
     * Current / total time label.
     */
    private final Label timeLabel;

    /**
     * Volume slider (0..100).
     */
    private final Slider volumeSlider;

    /**
     * Volume icon holder (muted / medium / max).
     */
    private final StackPane volumeGlyph;

    /**
     * Fullscreen toggle glyph holder.
     */
    private final StackPane fullGlyph;

    /**
     * Inline download progress, visible once a job starts.
     */
    private final ProgressBar downloadBar;

    /**
     * Time bubble shown while hovering the seek slider.
     */
    private final Popup seekPopup;

    /**
     * Time label inside the seek bubble.
     */
    private final Label seekTime;

    /**
     * Video title under the surface.
     */
    private final Label titleLabel;

    /**
     * Channel name row.
     */
    private final Label channelLabel;

    /**
     * Views / published metadata line in the description panel.
     */
    private final Label statsLabel;

    /**
     * Expandable description body.
     */
    private final Label descriptionLabel;

    /**
     * The related-videos list container (the cards). Reparented between
     * the side rail (wide) and the bottom host (narrow).
     */
    private final VBox relatedBox;

    /**
     * The side rail scroll pane (visible when the window is wide).
     */
    private ScrollPane relatedScroll;

    /**
     * Host below the description that holds the related list when the
     * window is too narrow for a side rail (YouTube-style stacking).
     */
    private VBox bottomRelated;

    /**
     * The main column scroll pane (video + info + bottom related).
     */
    private ScrollPane mainScroll;

    /**
     * Whether the related list is currently in the side rail.
     */
    private boolean relatedOnSide = true;

    /**
     * Current query driving the related rail (title seed, or a chrome
     * search performed while playing).
     */
    private String relatedQuery;

    /**
     * Continuation token for the next related page; {@code null} when
     * exhausted.
     */
    private String relatedToken;

    /**
     * Guards against concurrent related-page loads.
     */
    private boolean relatedLoading;

    /**
     * Ids already shown in the rail — continuation pages repeat
     * entries; dedup keeps the list clean.
     */
    private final Set<String> relatedSeen = new HashSet<>();

    /**
     * Nodes hidden while in fullscreen (everything but the surface).
     */
    private final VBox infoColumn;

    /**
     * Idle timer that fades the controls out during playback.
     */
    private final PauseTransition idleHide;

    /**
     * Listener moving the seek slider with playback time.
     */
    private ChangeListener<Duration> timeListener;

    /**
     * Fullscreen state listener (removed on release).
     */
    private ChangeListener<Boolean> fullScreenListener;

    /**
     * Key handler installed on the scene while this page is active.
     */
    private final EventHandler<KeyEvent> keyHandler;

    /**
     * Last seek target for gesture de-duplication.
     */
    private double lastSeekMillis = Double.NaN;

    /**
     * Timestamp of the last seek for gesture de-duplication.
     */
    private long lastSeekAtNanos = 0L;

    /**
     * Current signal state of this page.
     */
    private Signal signal = Signal.IGNORE;

    /**
     * Builds the watch page and starts resolving the streams.
     *
     * @param skeleton the hosting skeleton
     * @param video    the video to play
     */
    public PlayerPage(Skeleton<?> skeleton, VideoInfo video) {
        super(skeleton);
        this.video = video;
        getStyleClass().add("player-page");

        /*
         * ------------------------------------------------ video surface
         */
        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);

        loader = new KosmixDualLoader();
        /*
         * Slightly larger than the default so the splash loader reads
         * clearly on the big video surface.
         */
        loader.setRadius(13);
        loader.setVisible(true);
        loader.start();

        /*
         * Branded splash on the dark surface: glowing app icon with a
         * gentle breathing pulse, the product mark and the dual-circle
         * loader. It fades away the moment the first frame is ready.
         */
        final ImageView brandIcon = new ImageView();
        brandIcon.setFitWidth(72);
        brandIcon.setFitHeight(72);
        brandIcon.setPreserveRatio(true);
        brandIcon.setSmooth(true);
        if (!context().icons().isEmpty()) {
            brandIcon.setImage(context().icons().get(0));
        }
        brandIcon.setEffect(new DropShadow(28, Color.web("#ff1730", 0.65)));
        final ScaleTransition pulse = new ScaleTransition(Duration.seconds(1.4), brandIcon);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.08);
        pulse.setToY(1.08);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(ScaleTransition.INDEFINITE);
        pulse.play();

        final Label brandTitle = new Label("Kosmix Player");
        brandTitle.getStyleClass().add("brand-overlay-title");
        final Label brandSub = new Label("Tuning the best stream for you…");
        brandSub.getStyleClass().add("brand-overlay-sub");

        brandOverlay = new VBox(14, brandIcon, brandTitle, brandSub, loader);
        brandOverlay.getStyleClass().add("brand-overlay");
        brandOverlay.setAlignment(Pos.CENTER);
        brandOverlay.setMouseTransparent(true);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("player-error");
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);

        playGlyph = glyphButton("play", 20);
        playGlyph.getStyleClass().add("ctrl-btn");
        playGlyph.setOnMouseClicked(e -> togglePlay());

        final StackPane nextBtn = glyphButton("next", 18);
        nextBtn.getStyleClass().add("ctrl-btn");
        nextBtn.setOnMouseClicked(e -> playNextRelated());
        Tooltip.install(nextBtn, new Tooltip("Next (first related)"));

        final StackPane prevBtn = glyphButton("prev", 18);
        prevBtn.getStyleClass().add("ctrl-btn");
        prevBtn.setOnMouseClicked(e -> seekTo(0));
        Tooltip.install(prevBtn, new Tooltip("Restart"));

        seekSlider = new Slider(0, 1, 0);
        seekSlider.getStyleClass().add("seek-slider");
        HBox.setHgrow(seekSlider, Priority.ALWAYS);

        timeLabel = new Label("0:00 / 0:00");
        timeLabel.getStyleClass().add("time-label");

        volumeGlyph = glyphButton("max-vol", 18);
        volumeGlyph.getStyleClass().add("ctrl-btn");
        volumeGlyph.setOnMouseClicked(e -> toggleMute());

        volumeSlider = new Slider(0, 100, 70);
        volumeSlider.getStyleClass().add("volume-slider");
        volumeSlider.setPrefWidth(90);

        downloadBtn = glyphButton("download", 18);
        downloadBtn.getStyleClass().add("ctrl-btn");
        downloadBtn.setOnMouseClicked(e -> toggleDownloadMenu());
        Tooltip.install(downloadBtn, new Tooltip("Download MP4"));

        /*
         * Download-quality menu: pick the resolution before downloading.
         * In-surface overlay, anchored bottom-right above the controls.
         */
        downloadMenu = new VBox(2);
        downloadMenu.getStyleClass().add("quality-menu");
        downloadMenu.setPadding(new Insets(8));
        downloadMenu.setVisible(false);
        downloadMenu.setManaged(false);
        downloadMenu.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(downloadMenu, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(downloadMenu, new Insets(0, 56, 64, 0));
        /*
         * Clicks inside the menu must not bubble to the surface's
         * play/pause toggle.
         */
        downloadMenu.setOnMouseClicked(Event::consume);

        /*
         * Quality menu (gear in the control bar): Auto / Best plus the
         * concrete resolutions this video actually offers.
         */
        /*
         * Quality menu as an in-surface overlay (not a popup window) so
         * it stays clipped inside the player and themed by the scene.
         * Pinned bottom-right, just above the control bar.
         */
        qualityList = new VBox(2);
        qualityList.getStyleClass().add("quality-menu");
        qualityList.setPadding(new Insets(8));
        qualityList.setVisible(false);
        qualityList.setManaged(false);
        qualityList.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(qualityList, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(qualityList, new Insets(0, 14, 64, 0));
        /*
         * Clicks inside the menu must not bubble to the surface's
         * play/pause toggle.
         */
        qualityList.setOnMouseClicked(Event::consume);

        qualityBtn = glyphButton("settings", 17);
        qualityBtn.getStyleClass().add("ctrl-btn");
        qualityBtn.setOnMouseClicked(e -> toggleQualityMenu());
        Tooltip.install(qualityBtn, new Tooltip("Quality"));

        /*
         * Auto-dismiss the in-surface quality menu on any mouse press
         * outside it (the gear toggles it itself, so it is exempt).
         * A capturing filter on the page root sees every press first.
         */
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            final Object t = e.getTarget();
            final Node node = (t instanceof Node) ? (Node) t : null;
            if (qualityList.isVisible()
                    && !isWithin(node, qualityList) && !isWithin(node, qualityBtn)) {
                hideQualityMenu();
            }
            if (downloadMenu.isVisible()
                    && !isWithin(node, downloadMenu) && !isWithin(node, downloadBtn)) {
                hideDownloadMenu();
            }
        });

        downloadBar = new ProgressBar(0);
        downloadBar.getStyleClass().add("download-bar");
        downloadBar.setPrefWidth(70);
        downloadBar.setVisible(false);
        downloadBar.setManaged(false);

        fullGlyph = glyphButton("full-screen", 18);
        fullGlyph.getStyleClass().add("ctrl-btn");
        fullGlyph.setOnMouseClicked(e -> toggleFullScreen());

        final Region ctrlSpacer = new Region();
        HBox.setHgrow(ctrlSpacer, Priority.ALWAYS);

        final HBox buttonRow = new HBox(6,
                prevBtn, playGlyph, nextBtn, volumeGlyph, volumeSlider, timeLabel,
                ctrlSpacer, downloadBar, downloadBtn, qualityBtn, fullGlyph);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.setPadding(new Insets(0, 12, 6, 12));

        final HBox seekRow = new HBox(seekSlider);
        seekRow.setPadding(new Insets(4, 12, 4, 12));

        controls = new VBox(2, seekRow, buttonRow);
        controls.getStyleClass().add("player-controls");
        controls.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(controls, Pos.BOTTOM_CENTER);
        /*
         * Clicks on the control bar (buttons, sliders, quality menu
         * trigger) must never bubble to the surface's play/pause
         * toggle — only clicks on the video itself pause it.
         */
        controls.setOnMouseClicked(Event::consume);

        surface = new StackPane(mediaView, brandOverlay, errorLabel, controls,
                qualityList, downloadMenu);
        surface.getStyleClass().add("video-surface");
        surface.setMinHeight(220);
        /*
         * Zero min width + a hard clip: the control bar's natural width
         * must not force the surface (and the main column) wider than
         * its share of the window, and the video must never paint
         * outside the surface into the related rail when resizing.
         */
        surface.setMinWidth(0);
        controls.setMinWidth(0);
        final Rectangle surfaceClip = new Rectangle();
        surfaceClip.setArcWidth(28);
        surfaceClip.setArcHeight(28);
        surfaceClip.widthProperty().bind(surface.widthProperty());
        surfaceClip.heightProperty().bind(surface.heightProperty());
        /*
         * Square the clip corners in fullscreen (edge-to-edge), round
         * them otherwise to match the surface's 14px radius.
         */
        skeleton().window().fullScreenProperty().addListener((o, was, full) -> {
            surfaceClip.setArcWidth(full ? 0 : 28);
            surfaceClip.setArcHeight(full ? 0 : 28);
        });
        surface.setClip(surfaceClip);

        /*
         * Click toggles play/pause; double-click toggles fullscreen.
         */
        surface.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (e.getClickCount() == 2) {
                toggleFullScreen();
            } else {
                togglePlay();
            }
        });

        /*
         * Auto-hide the controls after a short idle while playing.
         */
        idleHide = new PauseTransition(Duration.seconds(2.6));
        idleHide.setOnFinished(e -> {
            if (player != null && player.getStatus() == MediaPlayer.Status.PLAYING) {
                fadeControls(false);
            }
        });
        surface.setOnMouseMoved(e -> pokeControls());
        surface.setOnMouseExited(e -> idleHide.playFromStart());

        /*
         * ----------------------------------------------- seek preview
         * Storyboard scrub-frames are disabled: YouTube serves the
         * sprite sheets only as WebP (the sqp param is required; without
         * it the URL 403s), and the skia-fx Image pipeline has no WebP
         * decoder. So the seek hover shows just a time bubble.
         */
        seekTime = new Label();
        seekTime.getStyleClass().add("seek-time");
        final VBox seekBox = new VBox(seekTime);
        seekBox.getStyleClass().add("seek-popup");
        seekBox.setAlignment(Pos.CENTER);
        seekBox.getStylesheets().add(context().stylesheet());
        seekPopup = new Popup();
        seekPopup.getContent().add(seekBox);
        wireSeekPreview(seekRow);

        /*
         * --------------------------------------------------- info area
         */
        titleLabel = new Label(video.title());
        titleLabel.getStyleClass().add("watch-title");
        titleLabel.setWrapText(true);

        channelLabel = new Label(video.channelName() == null ? "" : video.channelName());
        channelLabel.getStyleClass().add("watch-channel");

        statsLabel = new Label(buildStats(null));
        statsLabel.getStyleClass().add("watch-stats");

        descriptionLabel = new Label();
        descriptionLabel.getStyleClass().add("watch-description");
        descriptionLabel.setWrapText(true);

        final Label toggle = new Label("Show more");
        toggle.getStyleClass().add("watch-more");
        /*
         * Collapsed description shows a few lines; the toggle expands.
         */
        descriptionLabel.setMaxHeight(72);
        toggle.setOnMouseClicked(e -> {
            final boolean collapsed = descriptionLabel.getMaxHeight() < 1000;
            descriptionLabel.setMaxHeight(collapsed ? Double.MAX_VALUE : 72);
            toggle.setText(collapsed ? "Show less" : "Show more");
        });

        final VBox descriptionBox = new VBox(8, statsLabel, descriptionLabel, toggle);
        descriptionBox.getStyleClass().add("watch-description-box");
        descriptionBox.setPadding(new Insets(12));

        infoColumn = new VBox(10, titleLabel, channelLabel, descriptionBox);
        infoColumn.setPadding(new Insets(14, 0, 24, 0));

        /*
         * Bottom host for the related list when the window is too narrow
         * for a side rail — the list moves here, below the description,
         * and scrolls with the main page (YouTube's narrow layout).
         */
        bottomRelated = new VBox();
        bottomRelated.setMinWidth(0);
        bottomRelated.setVisible(false);
        bottomRelated.setManaged(false);

        /*
         * Main column scrolls as one page (video + info, like the
         * YouTube watch page) while the side list gets its own,
         * independent scroll. Searching from the chrome refreshes the
         * side list without interrupting playback.
         */
        final VBox mainColumn = new VBox(surface, infoColumn, bottomRelated);
        mainColumn.setPadding(new Insets(18, 4, 18, 24));
        mainColumn.setMinWidth(0);
        infoColumn.setMinWidth(0);

        mainScroll = new ScrollPane(mainColumn);
        mainScroll.getStyleClass().add("watch-scroll");
        mainScroll.setFitToWidth(true);
        mainScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mainScroll.setMinWidth(0);
        HBox.setHgrow(mainScroll, Priority.ALWAYS);
        /*
         * When the related list is stacked at the bottom it scrolls with
         * the main page, so the main scroll drives its infinite scroll.
         */
        mainScroll.vvalueProperty().addListener((obs, o, v) -> {
            if (!relatedOnSide && v.doubleValue() >= 0.9) {
                loadMoreRelated();
            }
        });

        /*
         * Video surface keeps 16:9 of the main column width, capped so
         * the control bar never leaves the viewport; in fullscreen it
         * fills the whole window instead.
         */
        surface.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> {
                    if (skeleton().window().isFullScreen()) {
                        return skeleton().window().getHeight();
                    }
                    final double byWidth = (mainColumn.getWidth() - 28) * 9.0 / 16.0;
                    final double cap = skeleton().window().getHeight() - 260;
                    return Math.max(220, Math.min(byWidth, cap));
                },
                mainColumn.widthProperty(),
                skeleton().window().heightProperty(),
                skeleton().window().fullScreenProperty()
        ));
        mediaView.fitWidthProperty().bind(surface.widthProperty());
        mediaView.fitHeightProperty().bind(surface.heightProperty());

        /*
         * ------------------------------------------------ side rail
         */
        relatedBox = new VBox(12);
        relatedBox.getStyleClass().add("related-box");
        relatedBox.setPadding(new Insets(18, 6, 18, 0));

        relatedScroll = new ScrollPane(relatedBox);
        relatedScroll.getStyleClass().add("watch-scroll");
        relatedScroll.setFitToWidth(true);
        relatedScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        relatedScroll.setPrefWidth(RELATED_WIDTH + 18);
        relatedScroll.setMinWidth(RELATED_WIDTH + 18);
        relatedScroll.setMaxWidth(RELATED_WIDTH + 18);
        /*
         * Infinite scroll while in the side rail: load the next page
         * near the bottom.
         */
        relatedScroll.vvalueProperty().addListener((obs, o, v) -> {
            if (relatedOnSide && v.doubleValue() >= 0.92) {
                loadMoreRelated();
            }
        });

        final HBox layout = new HBox(16, mainScroll, relatedScroll);
        anchor(layout, 0, 0, 0, 0);
        getChildren().add(layout);

        /*
         * Responsive: when the window is wide the related list sits in
         * the side rail; when it gets too narrow the rail would squeeze
         * the video, so the list moves below the description and scrolls
         * with the page (YouTube's narrow layout). Not applied in
         * fullscreen (everything but the surface is hidden).
         */
        widthProperty().addListener((obs, o, w) -> {
            if (!skeleton().window().isFullScreen()) {
                placeRelated(w.doubleValue() >= 980);
            }
        });

        /*
         * Fullscreen: hide everything but the surface and let it fill.
         * The main scroll's vertical bar is suppressed too, otherwise it
         * shows on the right edge over the video (the surface exactly
         * fills the viewport but rounding can leave a 1px overflow).
         */
        fullScreenListener = (obs, was, full) -> {
            infoColumn.setVisible(!full);
            infoColumn.setManaged(!full);
            relatedScroll.setVisible(!full && relatedOnSide);
            relatedScroll.setManaged(!full && relatedOnSide);
            bottomRelated.setVisible(!full && !relatedOnSide);
            bottomRelated.setManaged(!full && !relatedOnSide);
            mainColumn.setPadding(full ? Insets.EMPTY : new Insets(18, 4, 18, 24));
            mainScroll.setVbarPolicy(full
                    ? ScrollPane.ScrollBarPolicy.NEVER
                    : ScrollPane.ScrollBarPolicy.AS_NEEDED);
            mainScroll.setFitToHeight(full);
            /*
             * Edge-to-edge in fullscreen: drop the surface's rounded
             * corners so the video fills the whole window.
             */
            if (full) {
                surface.getStyleClass().add("fullscreen");
            } else {
                surface.getStyleClass().remove("fullscreen");
            }
            fullGlyph.getChildren().setAll(
                    Icons.icon(full ? "normal-screen" : "full-screen", 18, Color.WHITE));
        };
        skeleton().window().fullScreenProperty().addListener(fullScreenListener);

        /*
         * Keyboard shortcuts while the page is alive.
         */
        keyHandler = this::onKey;
        skeleton().window().getScene().addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);

        /*
         * Kick off stream resolution and the related rail.
         */
        resolve();
        loadRelated();
    }

    /**
     * Builds a white SVG glyph wrapped in a fixed-size button surface.
     *
     * @param icon the SVG asset name
     * @param size the glyph size
     * @return the button surface
     */
    private static StackPane glyphButton(String icon, double size) {
        final StackPane btn = new StackPane(Icons.icon(icon, size, Color.WHITE));
        btn.setPrefSize(38, 34);
        btn.setMinSize(38, 34);
        btn.setMaxSize(38, 34);
        return btn;
    }

    /**
     * Resolves the video details and starts dual-source playback with
     * the best video-only + audio-only pair (muxed fallback).
     */
    private void resolve() {
        context().youtube().details(video, d -> {
            this.details = d;
            statsLabel.setText(buildStats(d));
            descriptionLabel.setText(d.description() == null ? "" : d.description());
            if (d.channelName() != null) {
                channelLabel.setText(d.channelName());
            }
            startPlayback(d);
        }, err -> {
            loader.stop();
            loader.setVisible(false);
            showError("Could not load video — " + friendly(err));
        });
    }

    /**
     * Creates the {@link Media} / {@link MediaPlayer} pair for the
     * resolved formats and wires every listener.
     *
     * @param d the resolved details
     */
    private void startPlayback(VideoDetails d) {
        /*
         * The per-page menu override wins over the global quality
         * setting (Auto / Best / fixed height).
         */
        final Optional<Format> videoFmt = (qualityOverride == null)
                ? context().youtube().pickVideo(d)
                : YtService.bestVideo(d, qualityOverride);
        final Optional<Format> audioFmt = YtService.bestAudio(d);

        /*
         * Make the chosen quality auditable in the run log.
         */
        videoFmt.ifPresent(f -> System.out.println("[player] video stream: "
                + f.quality() + " " + f.vcodec() + " " + f.width() + "x" + f.height()
                + "@" + f.fps() + " (" + f.ext() + ", itag " + f.itag() + ")"));
        audioFmt.ifPresent(f -> System.out.println("[player] audio stream: "
                + f.bitrateKbps() + "kbps " + f.acodec()
                + " (" + f.ext() + ", itag " + f.itag() + ")"));

        final Media media;
        try {
            if (videoFmt.isPresent() && audioFmt.isPresent()) {
                /*
                 * Dual-source: separate streams, one synchronized
                 * player. The headers map applies to both streams and
                 * carries the per-format HTTP headers plus the custom
                 * User-Agent when configured.
                 */
                final Map<String, String> headers = new LinkedHashMap<>();
                if (videoFmt.get().httpHeaders() != null) {
                    headers.putAll(videoFmt.get().httpHeaders());
                }
                final String ua = context().settings().get(K.USER_AGENT, "");
                if (!ua.isBlank()) {
                    headers.put("User-Agent", ua);
                }
                media = new Media(audioFmt.get().url(), videoFmt.get().url(), headers);
            } else {
                final Format muxed = YtService.bestMuxed(d).orElseThrow(() ->
                        new IllegalStateException("No playable stream found"));
                media = new Media(muxed.url());
                if (muxed.httpHeaders() != null) {
                    media.setHeaders(muxed.httpHeaders());
                }
            }
        } catch (Throwable t) {
            loader.stop();
            loader.setVisible(false);
            showError("Could not open streams — " + friendly(t));
            return;
        }

        media.errorProperty().addListener((obs, o, err) -> {
            if (err != null) {
                showError("Playback error — " + friendly(err));
            }
        });

        try {
            player = new MediaPlayer(media);
        } catch (Throwable t) {
            loader.stop();
            loader.setVisible(false);
            showError("Player failed — " + friendly(t));
            return;
        }
        mediaView.setMediaPlayer(player);

        /*
         * Status → play glyph + controls visibility. Media property
         * listeners can fire off the FX thread (the dual-source engine
         * polls state on a background thread), so every UI mutation
         * hops to the FX thread.
         */
        player.statusProperty().addListener((obs, o, status) -> Context.update(() -> {
            final boolean playing = status == MediaPlayer.Status.PLAYING;
            playGlyph.getChildren().setAll(
                    Icons.icon(playing ? "pause" : "play", 20, Color.WHITE));
            if (!playing) {
                fadeControls(true);
            }
        }));
        player.setOnError(() -> {
            final Throwable err = player.getError() != null
                    ? player.getError() : media.getError();
            showError("Playback error — " + (err == null ? "unknown" : friendly(err)));
        });
        player.setOnEndOfMedia(() -> {
            /*
             * Autoplay-next is a user preference; when disabled the
             * player simply rests at the end.
             */
            if (context().settings().getBoolean(K.AUTOPLAY, true)) {
                playNextRelated();
            }
        });

        player.setOnReady(() -> {
            loader.stop();
            /*
             * The branded splash fades away as the first frame lands.
             */
            final FadeTransition splashOut = new FadeTransition(
                    Duration.millis(420), brandOverlay);
            splashOut.setToValue(0);
            splashOut.setOnFinished(e -> brandOverlay.setVisible(false));
            splashOut.play();

            final Duration total = media.getDuration();
            if (total != null && !total.isUnknown() && !total.isIndefinite()) {
                seekSlider.setMax(total.toMillis());
            }
            updateTime(player.getCurrentTime(), total);
            player.play();
            /*
             * After a quality switch, jump back to where the user was.
             */
            if (resumeAt != null) {
                final Duration target = resumeAt;
                resumeAt = null;
                seekTo(target.toMillis());
            }
        });

        /*
         * Playback time → slider + label (unless the user is dragging).
         */
        timeListener = (obs, o, now) -> {
            if (now == null) {
                return;
            }
            /*
             * currentTime can update off the FX thread — hop before
             * touching the slider and label.
             */
            final Duration total = media.getDuration();
            Context.update(() -> {
                if (!seekSlider.isValueChanging() && !seekSlider.isPressed()) {
                    seekSlider.setValue(now.toMillis());
                }
                updateTime(now, total);
            });
        };
        player.currentTimeProperty().addListener(timeListener);

        /*
         * Seek gestures: both triggers route through seekTo(), which
         * de-dupes the double fire of a drag.
         */
        seekSlider.setOnMouseReleased(e -> seekTo(seekSlider.getValue()));
        seekSlider.valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing) {
                seekTo(seekSlider.getValue());
            }
        });

        /*
         * Volume: restore the persisted value, keep the icon in sync
         * and persist changes.
         */
        final double savedVolume = context().settings().getDouble(K.VOLUME, 0.7);
        volumeSlider.setValue(savedVolume * 100);
        player.setVolume(savedVolume);
        volumeSlider.valueProperty().addListener((obs, o, value) -> {
            if (player != null) {
                player.setVolume(value.doubleValue() / 100.0);
                if (player.isMute() && value.doubleValue() > 0) {
                    player.setMute(false);
                }
            }
            context().settings().setDouble(K.VOLUME, value.doubleValue() / 100.0);
            updateVolumeGlyph();
        });
        player.muteProperty().addListener((obs, o, muted) -> updateVolumeGlyph());
        updateVolumeGlyph();
    }

    /**
     * Wires the storyboard hover preview to the whole seek row — a
     * larger hit target than the bare slider, matching the YouTube
     * behavior of previewing as soon as the pointer nears the bar.
     *
     * @param seekRow the row hosting the seek slider
     */
    private void wireSeekPreview(HBox seekRow) {
        seekRow.setOnMouseMoved(e -> {
            final Duration total = (player == null || player.getMedia() == null)
                    ? null : player.getMedia().getDuration();
            if (total == null || total.isUnknown() || total.isIndefinite()) {
                return;
            }
            /*
             * Horizontal fraction along the slider (the row pads the
             * slider by 12px on each side) → the time at that position.
             */
            final double width = Math.max(1, seekRow.getWidth() - 24);
            final double fraction = Math.min(1, Math.max(0, (e.getX() - 12) / width));
            final double seconds = total.toSeconds() * fraction;
            seekTime.setText(Strings.duration((long) seconds));
            final Bounds bounds = seekRow.localToScreen(seekRow.getBoundsInLocal());
            seekPopup.show(seekRow,
                    bounds.getMinX() + e.getX() - 28,
                    bounds.getMinY() - 40);
        });
        seekRow.setOnMouseExited(e -> seekPopup.hide());
    }

    /**
     * The single hardened entry point for every seek: clamps to the
     * playable range, refuses non-seekable states and collapses the
     * duplicate event a drag gesture produces.
     *
     * @param millis the requested position in milliseconds
     */
    private void seekTo(double millis) {
        final MediaPlayer p = player;
        if (p == null) {
            return;
        }
        final MediaPlayer.Status s = p.getStatus();
        if (s == null
                || s == MediaPlayer.Status.DISPOSED
                || s == MediaPlayer.Status.HALTED
                || s == MediaPlayer.Status.UNKNOWN) {
            return;
        }
        double target = Math.max(0.0, millis);
        final Duration dur = p.getMedia() != null ? p.getMedia().getDuration() : null;
        if (dur != null && !dur.isUnknown() && !dur.isIndefinite()) {
            final double maxMs = dur.toMillis();
            if (maxMs > 0.0) {
                /*
                 * Stay shy of the end so a seek-to-end doesn't race
                 * straight into end-of-media before a frame shows.
                 */
                target = Math.min(target, Math.max(0.0, maxMs - 100.0));
            }
        }
        final long now = System.nanoTime();
        if (!Double.isNaN(lastSeekMillis)
                && Math.abs(target - lastSeekMillis) < SEEK_DEDUP_MS
                && (now - lastSeekAtNanos) < SEEK_DEDUP_WINDOW_NS) {
            return;
        }
        lastSeekMillis = target;
        lastSeekAtNanos = now;

        seekSlider.setValue(target);
        try {
            p.seek(Duration.millis(target));
        } catch (Throwable t) {
            showError("Seek failed — " + friendly(t));
        }
    }

    /**
     * Toggles the in-surface quality menu, rebuilding its rows from the
     * resolutions this video offers.
     */
    private void toggleQualityMenu() {
        if (qualityList.isVisible()) {
            hideQualityMenu();
            return;
        }
        if (details == null) {
            return;
        }
        qualityList.getChildren().clear();
        qualityList.getChildren().add(qualityRow("Auto", null));
        qualityList.getChildren().add(qualityRow("Best available", 0));
        /*
         * Distinct descending heights actually available for this
         * video (plain https, no DRM).
         */
        details.formats().stream()
                .filter(Format::isVideoOnly)
                .filter(f -> !f.hasDrm())
                .filter(f -> "https".equals(f.protocol()))
                .map(Format::height)
                .filter(h -> h != null && h > 0)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .forEach(h -> qualityList.getChildren().add(qualityRow(h + "p", h)));

        qualityList.setVisible(true);
        qualityList.setManaged(true);
    }

    /**
     * Hides the in-surface quality menu.
     */
    private void hideQualityMenu() {
        qualityList.setVisible(false);
        qualityList.setManaged(false);
    }

    /**
     * Walks the parent chain to test whether {@code node} is the given
     * ancestor or a descendant of it.
     *
     * @param node     the node to test (may be {@code null})
     * @param ancestor the candidate ancestor
     * @return {@code true} when {@code node} is within {@code ancestor}
     */
    private static boolean isWithin(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds one row of the quality menu, marking the active choice.
     *
     * @param label the display label
     * @param cap   the height cap this row applies ({@code null} =
     *              follow the global setting, 0 = best available)
     * @return the row node
     */
    private Label qualityRow(String label, Integer cap) {
        final boolean active = (qualityOverride == null && cap == null)
                || (qualityOverride != null && qualityOverride.equals(cap));
        final Label row = new Label((active ? "✓  " : "    ") + label);
        row.getStyleClass().add("quality-item");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setOnMouseClicked(e -> {
            hideQualityMenu();
            switchQuality(cap);
        });
        return row;
    }

    /**
     * Restarts playback at the requested quality, preserving the
     * current position.
     *
     * @param cap the new per-page cap ({@code null} = global setting)
     */
    private void switchQuality(Integer cap) {
        this.qualityOverride = cap;
        if (details == null) {
            return;
        }
        if (player != null) {
            resumeAt = player.getCurrentTime();
            try {
                if (timeListener != null) {
                    player.currentTimeProperty().removeListener(timeListener);
                }
                player.stop();
                player.dispose();
            } catch (Throwable ignored) {
                /*
                 * A dying player must not block the quality switch.
                 */
            }
            player = null;
            mediaView.setMediaPlayer(null);
        }
        /*
         * Bring the branded splash back while the new streams spin up.
         */
        brandOverlay.setOpacity(1);
        brandOverlay.setVisible(true);
        loader.setVisible(true);
        loader.start();
        startPlayback(details);
    }

    /**
     * Toggles play/pause on the active player.
     */
    private void togglePlay() {
        if (player == null) {
            return;
        }
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.pause();
        } else {
            player.play();
        }
    }

    /**
     * Toggles audio mute.
     */
    private void toggleMute() {
        if (player != null) {
            player.setMute(!player.isMute());
        }
    }

    /**
     * Places the related list either in the side rail (wide window) or
     * stacked below the description (narrow window), reparenting the
     * single {@link #relatedBox} between the two hosts.
     *
     * @param wide {@code true} for the side rail, {@code false} for the
     *             bottom stack
     */
    private void placeRelated(boolean wide) {
        if (wide == relatedOnSide && relatedBox.getParent() != null) {
            return;
        }
        relatedOnSide = wide;
        if (wide) {
            /*
             * Side rail: detach from the bottom host and give it back to
             * the side scroll.
             */
            bottomRelated.getChildren().remove(relatedBox);
            bottomRelated.setVisible(false);
            bottomRelated.setManaged(false);
            relatedBox.setPadding(new Insets(18, 6, 18, 0));
            relatedScroll.setContent(relatedBox);
            relatedScroll.setVisible(true);
            relatedScroll.setManaged(true);
        } else {
            /*
             * Bottom stack: take it out of the side scroll and drop it
             * below the description, where it scrolls with the page.
             */
            relatedScroll.setContent(null);
            relatedScroll.setVisible(false);
            relatedScroll.setManaged(false);
            relatedBox.setPadding(new Insets(8, 0, 8, 0));
            if (!bottomRelated.getChildren().contains(relatedBox)) {
                bottomRelated.getChildren().setAll(relatedBox);
            }
            bottomRelated.setVisible(true);
            bottomRelated.setManaged(true);
        }
    }

    /**
     * Toggles OS fullscreen; the skeleton hides the chrome and this
     * page hides everything but the surface.
     */
    private void toggleFullScreen() {
        final var window = skeleton().window();
        window.setFullScreen(!window.isFullScreen());
    }

    /**
     * Swaps the volume glyph to match the mute state and level.
     */
    private void updateVolumeGlyph() {
        Context.update(() -> {
            final double value = volumeSlider.getValue();
            final boolean muted = (player != null && player.isMute()) || value <= 0.5;
            final String icon = muted ? "muted" : (value < 50 ? "medium-vol" : "max-vol");
            volumeGlyph.getChildren().setAll(Icons.icon(icon, 18, Color.WHITE));
        });
    }

    /**
     * Toggles the download-quality menu, rebuilding its rows (Auto /
     * Best / the resolutions this video offers) so the user picks the
     * quality before the download starts.
     */
    private void toggleDownloadMenu() {
        if (downloadMenu.isVisible()) {
            hideDownloadMenu();
            return;
        }
        if (details == null) {
            return;
        }
        hideQualityMenu();
        downloadMenu.getChildren().clear();
        final Label header = new Label("Download quality");
        header.getStyleClass().add("quality-header");
        downloadMenu.getChildren().add(header);
        downloadMenu.getChildren().add(downloadRow("Auto (current setting)", -1));
        downloadMenu.getChildren().add(downloadRow("Best available", 0));
        details.formats().stream()
                .filter(Format::isVideoOnly)
                .filter(f -> !f.hasDrm())
                .filter(f -> "https".equals(f.protocol()))
                .map(Format::height)
                .filter(h -> h != null && h > 0)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .forEach(h -> downloadMenu.getChildren().add(downloadRow(h + "p", h)));
        downloadMenu.setVisible(true);
        downloadMenu.setManaged(true);
    }

    /**
     * Hides the download-quality menu.
     */
    private void hideDownloadMenu() {
        downloadMenu.setVisible(false);
        downloadMenu.setManaged(false);
    }

    /**
     * Builds one row of the download-quality menu.
     *
     * @param label the display label
     * @param cap   the height cap to download with ({@code -1} = follow
     *              the configured quality, {@code 0} = best available)
     * @return the row node
     */
    private Label downloadRow(String label, int cap) {
        final Label row = new Label(label);
        row.getStyleClass().add("quality-item");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setOnMouseClicked(e -> {
            hideDownloadMenu();
            startDownload(cap);
        });
        return row;
    }

    /**
     * Starts a download job for this video at the chosen quality and
     * shows the inline progress bar.
     *
     * @param maxHeight the video height cap (see
     *                  {@link DownloadService#download(VideoDetails, int)})
     */
    private void startDownload(int maxHeight) {
        if (details == null) {
            return;
        }
        final DownloadService.Job job = context().downloads().download(details, maxHeight);
        downloadBar.setVisible(true);
        downloadBar.setManaged(true);
        downloadBar.progressProperty().bind(job.progressProperty());
        Tooltip.install(downloadBar, new Tooltip("Downloading to your Kosmix folder"));
    }

    /**
     * Plays the first entry of the related rail (used by the next
     * button and end-of-media).
     */
    private void playNextRelated() {
        for (var node : relatedBox.getChildren()) {
            if (node.getUserData() instanceof VideoInfo next) {
                skeleton().setProvider(new PlayerPage(skeleton(), next));
                return;
            }
        }
    }

    /**
     * Loads the related rail. ytnfx exposes no watch-next feed, so a
     * search seeded with the video title approximates YouTube's
     * related list.
     */
    private void loadRelated() {
        updateList(relatedSeed());
    }

    /**
     * Fills the side rail with the results of the given query without
     * touching playback — this is what a chrome search does while a
     * video is playing. Resets pagination so scrolling can load more.
     *
     * @param query the search query
     */
    public void updateList(String query) {
        relatedQuery = query;
        relatedToken = null;
        relatedLoading = true;
        relatedSeen.clear();
        relatedBox.getChildren().clear();
        context().youtube().search(query, null, page -> {
            relatedLoading = false;
            appendRelated(page.items());
            relatedToken = page.nextPageToken();
        }, err -> {
            /*
             * A failed list refresh is silent — never fatal for playback.
             */
            relatedLoading = false;
        });
    }

    /**
     * Loads the next page of related videos and appends it to the rail
     * (infinite scroll).
     */
    private void loadMoreRelated() {
        if (relatedLoading || relatedToken == null || relatedQuery == null) {
            return;
        }
        relatedLoading = true;
        context().youtube().search(relatedQuery, relatedToken, page -> {
            relatedLoading = false;
            appendRelated(page.items());
            relatedToken = page.nextPageToken();
        }, err -> {
            /*
             * A failed continuation is silent; scrolling again retries.
             */
            relatedLoading = false;
        });
    }

    /**
     * Appends new, de-duplicated, normal related videos to the rail
     * (excluding the currently playing video).
     *
     * @param fresh the page items to add
     */
    private void appendRelated(List<VideoInfo> fresh) {
        for (VideoInfo v : fresh) {
            if (v.isNormalVideo()
                    && !v.id().equals(video.id())
                    && relatedSeen.add(v.id())) {
                relatedBox.getChildren().add(relatedCard(v));
            }
        }
    }

    /**
     * Builds the search seed for the related rail.
     *
     * @return a query approximating "related videos"
     */
    private String relatedSeed() {
        final String title = video.title() == null ? "" : video.title();
        final String[] words = title.split("\\s+");
        final int keep = Math.min(words.length, 6);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keep; i++) {
            sb.append(words[i]).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Builds one compact related-video card (thumb left, text right).
     *
     * @param info the related video
     * @return the card node
     */
    private HBox relatedCard(VideoInfo info) {
        final AsyncThumb thumb = new AsyncThumb();
        thumb.setCornerRadius(8);
        thumb.setPrefSize(168, 94);
        thumb.setMinSize(168, 94);
        thumb.setMaxSize(168, 94);
        thumb.setUrl(
                VideoCard.jpegThumb(info.id(), "mqdefault"),
                VideoCard.jpegThumb(info.id(), "hqdefault"));

        final Label title = new Label(info.title());
        title.getStyleClass().add("related-title");
        title.setWrapText(true);
        title.setMaxHeight(40);

        final Label channel = new Label(info.channelName() == null ? "" : info.channelName());
        channel.getStyleClass().add("related-channel");

        final String views = Strings.compactCount(info.viewCount());
        final Label meta = new Label(views.isEmpty() ? "" : views + " views");
        meta.getStyleClass().add("related-meta");

        final VBox text = new VBox(3, title, channel, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        final HBox card = new HBox(10, thumb, text);
        card.getStyleClass().add("related-card");
        card.setUserData(info);
        card.setOnMouseClicked(e ->
                skeleton().setProvider(new PlayerPage(skeleton(), info)));
        return card;
    }

    /**
     * Keyboard shortcuts: space/K play-pause, F fullscreen, M mute,
     * arrows seek and volume.
     *
     * @param event the key event
     */
    private void onKey(KeyEvent event) {
        if (player == null) {
            return;
        }
        switch (event.getCode()) {
            case SPACE, K -> {
                togglePlay();
                event.consume();
            }
            case F -> toggleFullScreen();
            case M -> toggleMute();
            case LEFT -> seekTo(player.getCurrentTime().toMillis() - 5000);
            case RIGHT -> seekTo(player.getCurrentTime().toMillis() + 5000);
            case UP -> volumeSlider.setValue(Math.min(100, volumeSlider.getValue() + 5));
            case DOWN -> volumeSlider.setValue(Math.max(0, volumeSlider.getValue() - 5));
            default -> {
                /*
                 * Unhandled keys pass through.
                 */
            }
        }
    }

    /**
     * Shows the controls and re-arms the idle hide timer.
     */
    private void pokeControls() {
        fadeControls(true);
        idleHide.playFromStart();
    }

    /**
     * Fades the control bar in or out.
     *
     * @param show {@code true} to show, {@code false} to hide
     */
    private void fadeControls(boolean show) {
        final FadeTransition fade = new FadeTransition(Duration.millis(180), controls);
        fade.setToValue(show ? 1.0 : 0.0);
        fade.play();
    }

    /**
     * Updates the "current / total" time label.
     *
     * @param current the playback position
     * @param total   the media duration
     */
    private void updateTime(Duration current, Duration total) {
        final String cur = (current == null) ? "0:00"
                : Strings.duration((long) current.toSeconds());
        final String tot = (total == null || total.isUnknown() || total.isIndefinite())
                ? "0:00" : Strings.duration((long) total.toSeconds());
        Context.update(() -> timeLabel.setText(cur + " / " + tot));
    }

    /**
     * Builds the views / published stats line.
     *
     * @param d the details when resolved, may be {@code null}
     * @return the stats text
     */
    private String buildStats(VideoDetails d) {
        final Long viewCount = (d != null && d.viewCount() != null)
                ? d.viewCount() : video.viewCount();
        final String views = viewCount == null ? ""
                : Strings.groupedCount(viewCount) + " views";
        final String published = video.publishedText() == null ? "" : video.publishedText();
        if (!views.isEmpty() && !published.isEmpty()) {
            return views + " • " + published;
        }
        return views + published;
    }

    /**
     * Shows the error line on the surface. Safe to call from any thread
     * (media error properties fire on background threads).
     *
     * @param text the error text
     */
    private void showError(String text) {
        Context.update(() -> {
            loader.stop();
            loader.setVisible(false);
            errorLabel.setText(text);
            errorLabel.setVisible(true);
        });
    }

    /**
     * Maps an exception to a short, display-safe message.
     *
     * @param t the failure
     * @return a friendly message
     */
    private static String friendly(Throwable t) {
        final String msg = t.getMessage();
        return (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
    }

    /**
     * Returns the video this page plays (used by the navigation
     * history to recreate the page).
     *
     * @return the bound video
     */
    public VideoInfo video() {
        return video;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return "player";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void signal(Signal signal) {
        this.signal = signal;
        if (signal == Signal.STOP_PLAYBACK && player != null) {
            player.pause();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal signal() {
        return signal;
    }

    /**
     * Releases the player and every scene-level hook when the skeleton
     * navigates away.
     */
    @Override
    public void onRelease() {
        idleHide.stop();
        seekPopup.hide();
        skeleton().window().getScene().removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        skeleton().window().fullScreenProperty().removeListener(fullScreenListener);
        if (skeleton().window().isFullScreen()) {
            skeleton().window().setFullScreen(false);
        }
        if (player != null) {
            try {
                if (timeListener != null) {
                    player.currentTimeProperty().removeListener(timeListener);
                }
                player.stop();
                player.dispose();
            } catch (Throwable ignored) {
                /*
                 * Disposal must never break navigation.
                 */
            }
            player = null;
            mediaView.setMediaPlayer(null);
        }
    }
}
