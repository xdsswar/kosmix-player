package xss.it.kosmix.app.ui.views.pages;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.stage.DirectoryChooser;
import xss.it.kosmix.Launcher;
import xss.it.kosmix.app.K;
import xss.it.kosmix.app.ui.Skeleton;
import xss.it.kosmix.app.ui.provider.AbstractProvider;
import xss.it.kosmix.app.ui.provider.Page;
import xss.it.kosmix.app.ui.signal.Signal;

import java.io.File;
import java.nio.file.Paths;

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
 * Full-page application settings, opened from the title-bar gear,
 * organized in sections:
 * <ul>
 *   <li><b>Extraction</b> — client chain, custom User-Agent, home query;</li>
 *   <li><b>Playback</b> — maximum resolution, decode strategy, autoplay;</li>
 *   <li><b>Downloads</b> — output folder, parallel downloads, parallel
 *       mux operations, connections per stream, network timeout;</li>
 *   <li><b>About</b> — ffmpeg runtime status and app version.</li>
 * </ul>
 * Apply persists everything and pushes the changes into the running
 * services (ytnfx client rebuild, decode mode, download gates).
 */
public final class SettingsPage extends AbstractProvider implements Page {
    /**
     * Extraction clients exposed in the combo box; "auto" keeps the
     * ytnfx default chain (best quality coverage).
     */
    private static final String[] CLIENTS = {
            "auto", "web_safari", "android_vr", "ios", "web", "web_embedded", "mweb", "tv"
    };

    /**
     * Quality mode choices (display label → stored mode value).
     */
    private static final String[] QUALITY_LABELS = {
            "Auto (match network)", "Best available",
            "1080p", "720p", "480p", "360p"
    };

    /**
     * Stored mode values matching {@link #QUALITY_LABELS}.
     */
    private static final String[] QUALITY_VALUES = {
            K.QUALITY_AUTO, K.QUALITY_BEST, "1080", "720", "480", "360"
    };

    /**
     * Decode strategies of the skia-fx media engine.
     */
    private static final String[] DECODE_MODES = {"AUTO", "GPU_PREFERRED", "GPU", "CPU"};

    /**
     * Client selector.
     */
    private final ComboBox<String> clientBox;

    /**
     * Custom User-Agent field.
     */
    private final TextField userAgentField;

    /**
     * Default home query field.
     */
    private final TextField homeQueryField;

    /**
     * Maximum resolution selector.
     */
    private final ComboBox<String> resolutionBox;

    /**
     * Decode strategy selector.
     */
    private final ComboBox<String> decodeBox;

    /**
     * Autoplay-next toggle.
     */
    private final CheckBox autoplayCheck;

    /**
     * Download directory field (read-only; set via chooser).
     */
    private final TextField downloadDirField;

    /**
     * Maximum simultaneous downloads.
     */
    private final Spinner<Integer> maxDownloadsSpinner;

    /**
     * Maximum simultaneous mux operations.
     */
    private final Spinner<Integer> maxMixesSpinner;

    /**
     * Parallel connections per stream download.
     */
    private final Spinner<Integer> connectionsSpinner;

    /**
     * Network timeout in seconds.
     */
    private final Spinner<Integer> timeoutSpinner;

    /**
     * Current signal state of this page.
     */
    private Signal signal = Signal.IGNORE;

    /**
     * Builds the settings form.
     *
     * @param skeleton the hosting skeleton
     */
    public SettingsPage(Skeleton<?> skeleton) {
        super(skeleton);
        getStyleClass().add("settings-page");
        final var settings = context().settings();

        final Label heading = new Label("Settings");
        heading.getStyleClass().add("settings-heading");
        final Label sub = new Label("Extraction, playback and download preferences.");
        sub.getStyleClass().add("settings-sub");

        /*
         * ------------------------------------------------- extraction
         */
        clientBox = new ComboBox<>(FXCollections.observableArrayList(CLIENTS));
        clientBox.getSelectionModel().select(settings.get(K.CLIENT, K.DEFAULT_CLIENT));
        clientBox.setPrefWidth(280);

        userAgentField = new TextField(settings.get(K.USER_AGENT, ""));
        userAgentField.setPromptText("Custom User-Agent (blank = client default)");
        userAgentField.setPrefWidth(480);

        homeQueryField = new TextField(settings.get(K.HOME_QUERY, K.DEFAULT_HOME_QUERY));
        homeQueryField.setPrefWidth(380);

        final VBox extraction = section("Extraction",
                row("Client", clientBox),
                row("User-Agent", userAgentField),
                row("Home query", homeQueryField));

        /*
         * --------------------------------------------------- playback
         */
        resolutionBox = new ComboBox<>(FXCollections.observableArrayList(QUALITY_LABELS));
        resolutionBox.getSelectionModel().select(
                qualityIndex(settings.get(K.QUALITY, K.DEFAULT_QUALITY)));
        resolutionBox.setPrefWidth(280);

        decodeBox = new ComboBox<>(FXCollections.observableArrayList(DECODE_MODES));
        decodeBox.getSelectionModel().select(settings.get(K.DECODE_MODE, "AUTO"));
        decodeBox.setPrefWidth(280);

        autoplayCheck = new CheckBox("Autoplay the next video when one ends");
        autoplayCheck.setSelected(settings.getBoolean(K.AUTOPLAY, true));

        final VBox playback = section("Playback",
                row("Quality", resolutionBox),
                row("Decode", decodeBox),
                row("Autoplay", autoplayCheck));

        /*
         * -------------------------------------------------- downloads
         */
        downloadDirField = new TextField(settings.get(
                K.DOWNLOAD_DIR,
                Paths.get(System.getProperty("user.home"), "Downloads", "Kosmix").toString()));
        downloadDirField.setEditable(false);
        downloadDirField.setPrefWidth(380);

        final Button browse = new Button("Browse…");
        browse.getStyleClass().add("settings-btn");
        browse.setOnAction(e -> {
            final DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose download folder");
            final File current = new File(downloadDirField.getText());
            if (current.isDirectory()) {
                chooser.setInitialDirectory(current);
            }
            final File picked = chooser.showDialog(skeleton().window());
            if (picked != null) {
                downloadDirField.setText(picked.getAbsolutePath());
            }
        });
        final HBox dirRow = new HBox(8, downloadDirField, browse);
        dirRow.setAlignment(Pos.CENTER_LEFT);

        maxDownloadsSpinner = new Spinner<>(1, 6, settings.getInteger(K.MAX_DOWNLOADS, 3));
        maxDownloadsSpinner.setPrefWidth(110);
        maxMixesSpinner = new Spinner<>(1, 4, settings.getInteger(K.MAX_MIXES, 2));
        maxMixesSpinner.setPrefWidth(110);
        connectionsSpinner = new Spinner<>(1, 8, settings.getInteger(K.DL_CONNECTIONS, 4));
        connectionsSpinner.setPrefWidth(110);
        timeoutSpinner = new Spinner<>(10, 300, settings.getInteger(K.NET_TIMEOUT, 60), 10);
        timeoutSpinner.setPrefWidth(110);

        final VBox downloads = section("Downloads",
                row("Output folder", dirRow),
                row("Max downloads", labeled(maxDownloadsSpinner, "simultaneous downloads")),
                row("Max mixes", labeled(maxMixesSpinner, "simultaneous mux operations")),
                row("Connections", labeled(connectionsSpinner, "parallel connections per stream")),
                row("Timeout", labeled(timeoutSpinner, "seconds")));

        /*
         * ------------------------------------------------------ about
         */
        final String ffmpegStatus = Media.isFfmpegAvailable()
                ? "Available — " + safeStatus()
                : "Not loaded yet (downloads on first run; WebM/Opus playback "
                + "and muxing activate once ready)";
        final Label ffmpegLabel = new Label(ffmpegStatus);
        ffmpegLabel.getStyleClass().add("settings-ffmpeg");
        ffmpegLabel.setWrapText(true);

        final Label about = new Label(context().name() + " " + context().version()
                + " — powered by skia-fx, ytnfx and nfx-listview");
        about.getStyleClass().add("settings-about");

        final VBox aboutBox = section("About",
                row("ffmpeg", ffmpegLabel),
                row("Version", about));

        /*
         * ---------------------------------------------------- actions
         */
        final Button apply = new Button("Apply");
        apply.getStyleClass().addAll("settings-btn", "settings-apply");
        apply.setOnAction(e -> applyChanges());

        final Button back = new Button("Back");
        back.getStyleClass().add("settings-btn");
        back.setOnAction(e -> skeleton().setProvider(skeleton().home()));

        final HBox actions = new HBox(10, apply, back);
        actions.setAlignment(Pos.CENTER_LEFT);

        final VBox content = new VBox(18, heading, sub,
                extraction, playback, downloads, aboutBox, actions);
        content.setPadding(new Insets(28, 36, 36, 36));
        content.setMaxWidth(860);

        final ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("watch-scroll");
        scroll.setFitToWidth(true);
        anchor(scroll, 0, 0, 0, 0);
        getChildren().add(scroll);
    }

    /**
     * Builds a titled settings section card.
     *
     * @param title the section title
     * @param rows  the form rows
     * @return the section node
     */
    private static VBox section(String title, Node... rows) {
        final Label header = new Label(title);
        header.getStyleClass().add("settings-section-title");
        final VBox box = new VBox(2);
        box.getStyleClass().add("settings-form");
        box.setPadding(new Insets(14, 20, 16, 20));
        box.getChildren().add(header);
        box.getChildren().addAll(rows);
        return box;
    }

    /**
     * Builds one labeled form row.
     *
     * @param label   the row label
     * @param control the row control
     * @return the row node
     */
    private static HBox row(String label, Region control) {
        final Label l = new Label(label);
        l.getStyleClass().add("settings-label");
        l.setMinWidth(130);
        l.setPrefWidth(130);
        final HBox row = new HBox(16, l, control);
        HBox.setHgrow(control, Priority.SOMETIMES);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 0, 9, 0));
        return row;
    }

    /**
     * Pairs a control with a trailing hint label.
     *
     * @param control the control
     * @param hint    the hint text
     * @return the paired row content
     */
    private static HBox labeled(Region control, String hint) {
        final Label l = new Label(hint);
        l.getStyleClass().add("settings-hint");
        final HBox box = new HBox(10, control, l);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Maps a stored quality mode value to its combo index.
     *
     * @param mode the stored quality mode
     * @return the matching index (0 = auto)
     */
    private static int qualityIndex(String mode) {
        for (int i = 0; i < QUALITY_VALUES.length; i++) {
            if (QUALITY_VALUES[i].equalsIgnoreCase(mode)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Persists every field and pushes the changes into the running
     * services so they take effect immediately.
     */
    private void applyChanges() {
        final var settings = context().settings();
        settings.set(K.CLIENT, clientBox.getSelectionModel().getSelectedItem());
        settings.set(K.USER_AGENT, userAgentField.getText() == null
                ? "" : userAgentField.getText().trim());
        settings.set(K.HOME_QUERY, homeQueryField.getText() == null
                ? K.DEFAULT_HOME_QUERY : homeQueryField.getText().trim());
        settings.set(K.QUALITY,
                QUALITY_VALUES[resolutionBox.getSelectionModel().getSelectedIndex()]);
        settings.set(K.DECODE_MODE, decodeBox.getSelectionModel().getSelectedItem());
        settings.setBoolean(K.AUTOPLAY, autoplayCheck.isSelected());
        settings.set(K.DOWNLOAD_DIR, downloadDirField.getText());
        settings.setInteger(K.MAX_DOWNLOADS, maxDownloadsSpinner.getValue());
        settings.setInteger(K.MAX_MIXES, maxMixesSpinner.getValue());
        settings.setInteger(K.DL_CONNECTIONS, connectionsSpinner.getValue());
        settings.setInteger(K.NET_TIMEOUT, timeoutSpinner.getValue());

        /*
         * Live propagation: extraction client, decode strategy and the
         * download concurrency gates.
         */
        context().youtube().rebuild();
        context().downloads().applySettings();
        Launcher.applyDecodeMode(decodeBox.getSelectionModel().getSelectedItem());
        skeleton().setProvider(skeleton().home());
    }

    /**
     * Reads the media engine's ffmpeg status defensively.
     *
     * @return a short status line
     */
    private static String safeStatus() {
        try {
            final String status = Media.getFfmpegStatus();
            return status == null ? "loaded" : status;
        } catch (Throwable t) {
            return "loaded";
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return "settings";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void signal(Signal signal) {
        this.signal = signal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal signal() {
        return signal;
    }
}
