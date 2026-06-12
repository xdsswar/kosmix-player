package xss.it.kosmix.app.ui.views.widget;

import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.app.services.DownloadService;

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
 * Chrome-style downloads history button for the title bar: a download
 * glyph with a small active-count badge that opens a popup listing
 * every download of the session (newest first) with its title, live
 * status, progress bar and a folder button to reveal the finished file
 * in the system file browser.
 */
public final class DownloadsButton extends StackPane {
    /**
     * The application context (download service, host services).
     */
    private final Context context;

    /**
     * Active-download count badge pinned to the icon corner.
     */
    private final Label counter;

    /**
     * The history popup.
     */
    private final Popup popup;

    /**
     * The vertical list of job rows inside the popup.
     */
    private final VBox list;

    /**
     * Builds the button and subscribes to the job list.
     *
     * @param context the application context
     */
    public DownloadsButton(Context context) {
        super();
        this.context = context;
        getStyleClass().add("chrome-btn");
        setPrefSize(40, 40);
        setMinSize(40, 40);
        setMaxSize(40, 40);
        Tooltip.install(this, new Tooltip("Downloads"));

        getChildren().add(Icons.icon("download", 18, Color.web("#0f0f0f")));

        counter = new Label();
        counter.getStyleClass().add("downloads-counter");
        counter.setVisible(false);
        StackPane.setAlignment(counter, Pos.TOP_RIGHT);
        getChildren().add(counter);

        /*
         * Popup content: a themed card with a header and a scrolling
         * list of job rows. Popups live outside the scene root, so the
         * stylesheet is attached explicitly.
         */
        final Label header = new Label("Downloads");
        header.getStyleClass().add("downloads-title");

        list = new VBox(6);
        list.setPadding(new Insets(4, 0, 0, 0));

        final ScrollPane scroll = new ScrollPane(list);
        scroll.getStyleClass().add("downloads-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(420);

        final VBox card = new VBox(8, header, scroll);
        card.getStyleClass().add("downloads-card");
        card.setPadding(new Insets(14));
        card.setPrefWidth(380);
        card.getStylesheets().add(context.stylesheet());

        popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(card);

        setOnMouseClicked(e -> toggle());

        context.downloads().jobs().addListener(
                (ListChangeListener<DownloadService.Job>) change -> {
                    refreshCounter();
                    if (popup.isShowing()) {
                        rebuild();
                    }
                });
        refreshCounter();
    }

    /**
     * Toggles the history popup anchored under the button.
     */
    private void toggle() {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        rebuild();
        final Bounds b = localToScreen(getBoundsInLocal());
        if (b != null) {
            popup.show(this, b.getMaxX() - 380, b.getMaxY() + 6);
        }
    }

    /**
     * Rebuilds the job rows in the popup.
     */
    private void rebuild() {
        list.getChildren().clear();
        final var jobs = context.downloads().jobs();
        if (jobs.isEmpty()) {
            final Label empty = new Label("No downloads yet");
            empty.getStyleClass().add("downloads-empty");
            list.getChildren().add(empty);
            return;
        }
        jobs.forEach(job -> list.getChildren().add(row(job)));
    }

    /**
     * Builds one history row for a job.
     *
     * @param job the download job
     * @return the row node
     */
    private HBox row(DownloadService.Job job) {
        final Label title = new Label(job.video().title());
        title.getStyleClass().add("download-title");
        title.setMaxWidth(280);

        final Label status = new Label();
        status.getStyleClass().add("download-status");
        status.textProperty().bind(job.statusProperty());

        final ProgressBar bar = new ProgressBar(0);
        bar.getStyleClass().add("download-bar");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.progressProperty().bind(job.progressProperty());

        final VBox text = new VBox(3, title, status, bar);
        HBox.setHgrow(text, Priority.ALWAYS);

        /*
         * Folder button reveals the finished file in the system file
         * browser; disabled until the job completes.
         */
        final StackPane folder = new StackPane(Icons.icon("folder", 18, Color.web("#0f0f0f")));
        folder.getStyleClass().add("download-folder");
        folder.setPrefSize(34, 34);
        folder.setMinSize(34, 34);
        folder.setMaxSize(34, 34);
        folder.setOnMouseClicked(e -> {
            if (job.output() != null) {
                context.downloads().reveal(job.output());
            }
        });
        updateFolderEnabled(folder, job);
        job.stateProperty().addListener((obs, o, s) -> {
            updateFolderEnabled(folder, job);
            if (s == DownloadService.Job.State.DONE) {
                bar.setVisible(false);
                bar.setManaged(false);
            }
            refreshCounter();
        });

        final HBox row = new HBox(10, text, folder);
        row.getStyleClass().add("download-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        return row;
    }

    /**
     * Enables the folder button only for finished jobs.
     *
     * @param folder the folder button
     * @param job    the job
     */
    private void updateFolderEnabled(Region folder, DownloadService.Job job) {
        final boolean done = job.stateProperty().get() == DownloadService.Job.State.DONE
                && job.output() != null;
        folder.setOpacity(done ? 1.0 : 0.35);
        folder.setDisable(!done);
    }

    /**
     * Updates the active-download count badge.
     */
    private void refreshCounter() {
        final long running = context.downloads().jobs().stream()
                .filter(j -> j.stateProperty().get() == DownloadService.Job.State.RUNNING)
                .count();
        counter.setText(String.valueOf(running));
        counter.setVisible(running > 0);
    }
}
