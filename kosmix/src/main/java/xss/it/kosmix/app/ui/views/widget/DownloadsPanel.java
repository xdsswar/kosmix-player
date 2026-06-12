package xss.it.kosmix.app.ui.views.widget;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
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
 * Global download progress surface: a stack of toast cards pinned to
 * the bottom-right of the skeleton, one per download job, each with
 * the video title, the live phase line (downloading video / audio,
 * waiting for a slot, muxing) and a progress bar covering the whole
 * pipeline including the mux. Finished toasts linger briefly (click
 * opens the file), failed ones stay a little longer, then fade away.
 */
public final class DownloadsPanel extends VBox {
    /**
     * How long a finished toast stays visible before fading.
     */
    private static final Duration LINGER_DONE = Duration.seconds(6);

    /**
     * How long a failed toast stays visible before fading.
     */
    private static final Duration LINGER_FAILED = Duration.seconds(12);

    /**
     * The application context (download service, host services).
     */
    private final Context context;

    /**
     * Builds the panel and subscribes to the job list.
     *
     * @param context the application context
     */
    public DownloadsPanel(Context context) {
        super(10);
        this.context = context;
        getStyleClass().add("downloads-panel");
        setAlignment(Pos.BOTTOM_RIGHT);
        setPickOnBounds(false);
        setMaxWidth(360);

        /*
         * One toast per new job; the panel itself never blocks clicks
         * outside its cards.
         */
        context.downloads().jobs().addListener(
                (ListChangeListener<DownloadService.Job>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            change.getAddedSubList().forEach(job ->
                                    getChildren().add(0, toast(job)));
                        }
                    }
                });
    }

    /**
     * Builds one toast card bound to a job.
     *
     * @param job the download job
     * @return the toast node
     */
    private VBox toast(DownloadService.Job job) {
        final Label title = new Label(job.video().title());
        title.getStyleClass().add("download-title");
        title.setMaxWidth(320);

        final Label status = new Label();
        status.getStyleClass().add("download-status");
        status.textProperty().bind(job.statusProperty());

        final Label percent = new Label("0%");
        percent.getStyleClass().add("download-percent");
        job.progressProperty().addListener((obs, o, p) ->
                percent.setText(Math.round(p.doubleValue() * 100) + "%"));

        final HBox statusRow = new HBox(8, status, spacer(), percent);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        final ProgressBar bar = new ProgressBar(0);
        bar.getStyleClass().add("download-bar");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.progressProperty().bind(job.progressProperty());

        final VBox toast = new VBox(6, title, statusRow, bar);
        toast.getStyleClass().add("download-toast");
        toast.setPadding(new Insets(12));

        /*
         * Terminal behavior: recolor, allow opening the result and
         * fade out after a short linger.
         */
        job.stateProperty().addListener((obs, o, state) -> {
            if (state == DownloadService.Job.State.DONE) {
                toast.getStyleClass().add("download-done");
                toast.setOnMouseClicked(e -> {
                    if (job.output() != null) {
                        context.services().showDocument(job.output().toUri().toString());
                    }
                });
                dismissLater(toast, LINGER_DONE);
            } else if (state == DownloadService.Job.State.FAILED) {
                toast.getStyleClass().add("download-failed");
                dismissLater(toast, LINGER_FAILED);
            }
        });
        return toast;
    }

    /**
     * Fades a toast out and removes it after the given linger time.
     *
     * @param toast  the toast node
     * @param linger how long to keep it visible first
     */
    private void dismissLater(VBox toast, Duration linger) {
        final PauseTransition wait = new PauseTransition(linger);
        wait.setOnFinished(e -> {
            final FadeTransition fade = new FadeTransition(Duration.millis(350), toast);
            fade.setToValue(0);
            fade.setOnFinished(done -> getChildren().remove(toast));
            fade.play();
        });
        wait.play();
    }

    /**
     * Builds a horizontally growing spacer for the status row.
     *
     * @return the spacer region
     */
    private static HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
