package xss.it.kosmix.app.services;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.media.MediaMixer;
import javafx.scene.media.MediaMixerListener;
import xss.it.download.Downloader;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.app.K;
import xss.it.kosmix.helper.platform.Platform;
import xss.it.model.Format;
import xss.it.model.VideoDetails;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.services package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * Orchestrates video downloads: fetches the best video-only and
 * audio-only streams through the ytnfx {@link Downloader} (parallel,
 * chunked, with progress) into a temp folder, then muxes them into a
 * single MP4 with skia-fx's {@link MediaMixer} (lossless stream copy).
 * Each download is represented by an observable {@link Job} so any UI
 * can bind progress bars and status text directly.
 */
public final class DownloadService {
    /**
     * Progress weight of the video stream phase (the heaviest part).
     */
    private static final double VIDEO_SPAN = 0.60;

    /**
     * Progress weight of the audio stream phase.
     */
    private static final double AUDIO_SPAN = 0.15;

    /**
     * Progress weight of the final mux phase.
     */
    private static final double MUX_SPAN = 0.25;

    /**
     * The application context (settings, executor).
     */
    private final Context context;

    /**
     * Live list of every job started this session (newest first); UI
     * components observe it to render progress.
     */
    private final ObservableList<Job> jobs = FXCollections.observableArrayList();

    /**
     * Gate limiting how many downloads run at the same time
     * ({@link K#MAX_DOWNLOADS}); rebuilt by {@link #applySettings()}.
     */
    private volatile Semaphore downloadSlots;

    /**
     * Gate limiting how many mux operations run at the same time
     * ({@link K#MAX_MIXES}); rebuilt by {@link #applySettings()}.
     */
    private volatile Semaphore mixSlots;

    /**
     * Creates the download service.
     *
     * @param context the application context
     */
    public DownloadService(Context context) {
        this.context = context;
        applySettings();
    }

    /**
     * Re-reads the concurrency limits from the settings. Jobs already
     * holding a permit are unaffected; new jobs use the fresh gates.
     */
    public void applySettings() {
        downloadSlots = new Semaphore(Math.max(1,
                context.settings().getInteger(K.MAX_DOWNLOADS, 3)));
        mixSlots = new Semaphore(Math.max(1,
                context.settings().getInteger(K.MAX_MIXES, 2)));
    }

    /**
     * Returns the observable list of download jobs (newest first).
     *
     * @return the jobs list
     */
    public ObservableList<Job> jobs() {
        return jobs;
    }

    /**
     * Resolves the configured download directory, defaulting to
     * {@code <user home>\Downloads\Kosmix} and creating it on demand.
     *
     * @return the download directory path
     * @throws IOException when the directory cannot be created
     */
    public Path downloadDir() throws IOException {
        final String configured = context.settings().get(
                K.DOWNLOAD_DIR,
                Paths.get(System.getProperty("user.home"), "Downloads", "Kosmix").toString()
        );
        final Path dir = Paths.get(configured);
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Starts a download + mux job at the configured playback quality.
     *
     * @param details the resolved video details to download
     * @return the observable job, already added to {@link #jobs()}
     */
    public Job download(VideoDetails details) {
        return download(details, -1);
    }

    /**
     * Starts a download + mux job for the given video at a chosen
     * maximum video height. Stream selection follows playback logic
     * (best video-only ≤ cap + best audio-only); when no separate pair
     * exists the best muxed stream is downloaded directly without a mux
     * phase.
     *
     * @param details   the resolved video details to download
     * @param maxHeight the maximum video height in pixels; {@code 0} for
     *                  the best available, or {@code -1} to follow the
     *                  configured playback quality
     * @return the observable job, already added to {@link #jobs()}
     */
    public Job download(VideoDetails details, int maxHeight) {
        final Job job = new Job(details);
        jobs.add(0, job);
        context.executor().execute(() -> run(job, maxHeight));
        return job;
    }

    /**
     * Opens the system file browser at the given file (selected) or its
     * containing folder, so the user can reveal a finished download.
     *
     * @param file the file to reveal, or {@code null} to open the
     *             download directory
     */
    public void reveal(Path file) {
        context.executor().execute(() -> {
            try {
                if (file != null && Files.exists(file)) {
                    /*
                     * Explorer /select highlights the file in its folder.
                     */
                    new ProcessBuilder("explorer.exe", "/select,",
                            file.toAbsolutePath().toString()).start();
                } else {
                    final Path dir = (file != null) ? file.getParent() : downloadDir();
                    new ProcessBuilder("explorer.exe",
                            dir.toAbsolutePath().toString()).start();
                }
            } catch (Exception e) {
                /*
                 * Fall back to the host services on any failure.
                 */
                final Path target = (file != null && file.getParent() != null)
                        ? file.getParent() : null;
                if (target != null) {
                    Context.update(() ->
                            context.services().showDocument(target.toUri().toString()));
                }
            }
        });
    }

    /**
     * Executes the blocking pipeline of a job on a background thread:
     * video download → audio download → mux → cleanup.
     *
     * @param job       the job to execute
     * @param maxHeight the chosen video height cap (see
     *                  {@link #download(VideoDetails, int)})
     */
    private void run(Job job, int maxHeight) {
        final Semaphore gate = downloadSlots;
        try {
            /*
             * Respect the configured parallel-download limit; queued
             * jobs simply wait for a slot.
             */
            job.status("Waiting for a download slot…");
            gate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.fail(e);
            return;
        }
        /*
         * Tracks whether the download slot was already handed back
         * (it frees before the mux phase); the finally block releases
         * it on every other path exactly once.
         */
        boolean released = false;
        try {
            final VideoDetails video = job.video();
            /*
             * maxHeight: -1 = follow the configured playback quality,
             * 0 = best available, otherwise an explicit height cap.
             */
            final Optional<Format> videoFmt = (maxHeight < 0)
                    ? context.youtube().pickVideo(video)
                    : YtService.bestVideo(video, maxHeight);
            final Optional<Format> audioFmt = YtService.bestAudio(video);

            final Path outDir = downloadDir();
            final Path target = outDir.resolve(safeName(video.title()) + ".mp4");

            /*
             * Temp staging area for the raw streams, cleaned at the end.
             */
            final Path tmpDir = Paths.get(
                    Platform.getProgramDataDir(), ".kosmix", "tmp", video.id());
            Files.createDirectories(tmpDir);

            /*
             * Parallel connections per stream, user-tunable.
             */
            final int connections = Math.max(1, Math.min(8,
                    context.settings().getInteger(K.DL_CONNECTIONS, 4)));

            if (videoFmt.isPresent() && audioFmt.isPresent()) {
                final Format vf = videoFmt.get();
                final Format af = audioFmt.get();
                final Path videoPath = tmpDir.resolve("video." + vf.ext());
                final Path audioPath = tmpDir.resolve("audio." + af.ext());

                /*
                 * Phase 1+2: stream downloads with weighted progress.
                 */
                job.status("Downloading video…");
                final Downloader downloader = new Downloader(connections, 8 * 1024 * 1024);
                downloader.download(vf, videoPath, progress ->
                        job.progress(VIDEO_SPAN * progress.fraction()));

                job.status("Downloading audio…");
                downloader.download(af, audioPath, progress ->
                        job.progress(VIDEO_SPAN + AUDIO_SPAN * progress.fraction()));

                /*
                 * The download slot frees up before the mux phase so
                 * the next queued download can start while this job
                 * muxes under its own concurrency gate.
                 */
                gate.release();
                released = true;
                job.status("Waiting for a mux slot…");
                mixSlots.acquire();

                /*
                 * Phase 3: lossless mux into a single MP4. MediaMixer
                 * reports through FX-thread callbacks; completion is
                 * forwarded to the job and the temp files are removed.
                 */
                job.status("Muxing…");
                final MediaMixer mixer = new MediaMixer(
                        audioPath.toString(), videoPath.toString(), target.toString());
                mixer.setListener(new MediaMixerListener() {
                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void onProgress(double p) {
                        job.progress(VIDEO_SPAN + AUDIO_SPAN + MUX_SPAN * p);
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void onFinished(String path) {
                        mixSlots.release();
                        job.finish(target);
                        cleanup(tmpDir);
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void onError(String message) {
                        mixSlots.release();
                        job.fail(new IOException(message));
                        cleanup(tmpDir);
                    }
                });
                mixer.start(context.executor());
            } else {
                /*
                 * Fallback: no separate pair — download the best muxed
                 * stream straight to the target (no mux phase needed).
                 */
                final Format mf = YtService.bestMuxed(video).orElseThrow(() ->
                        new IOException("No downloadable stream available"));
                job.status("Downloading…");
                final Path direct = outDir.resolve(safeName(video.title()) + "." + mf.ext());
                new Downloader(connections, 8 * 1024 * 1024).download(mf, direct, progress ->
                        job.progress(progress.fraction()));
                job.finish(direct);
                cleanup(tmpDir);
            }
        } catch (Throwable t) {
            job.fail(t);
        } finally {
            /*
             * Release the same gate instance that was acquired —
             * applySettings() may have swapped the field meanwhile.
             */
            if (!released) {
                gate.release();
            }
        }
    }

    /**
     * Best-effort removal of a job's temp staging directory.
     *
     * @param tmpDir the directory to remove
     */
    private void cleanup(Path tmpDir) {
        context.executor().execute(() -> {
            try (var walk = Files.walk(tmpDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        /*
                         * Locked leftovers are swept on the next run.
                         */
                    }
                });
            } catch (IOException ignored) {
                /*
                 * Missing dir means nothing to clean.
                 */
            }
        });
    }

    /**
     * Sanitizes a video title into a safe Windows file name.
     *
     * @param title the raw video title
     * @return a filesystem-safe name, trimmed to a sane length
     */
    private static String safeName(String title) {
        String name = (title == null || title.isBlank()) ? "video" : title;
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return name.length() > 120 ? name.substring(0, 120) : name;
    }

    /**
     * Observable state of a single download: weighted progress over the
     * video/audio/mux phases, human status text and a terminal state.
     */
    public static final class Job {
        /**
         * The video being downloaded.
         */
        private final VideoDetails video;

        /**
         * Aggregated progress over all phases, {@code 0..1}.
         */
        private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", 0);

        /**
         * Human readable status line ("Downloading video…", "Muxing…").
         */
        private final StringProperty status = new SimpleStringProperty(this, "status", "Queued…");

        /**
         * Terminal state of the job; {@code null} while running.
         */
        private final ObjectProperty<State> state = new SimpleObjectProperty<>(this, "state", State.RUNNING);

        /**
         * The produced file once the job finished successfully.
         */
        private volatile Path output;

        /**
         * The failure cause once the job failed.
         */
        private volatile Throwable error;

        /**
         * Creates the job wrapper.
         *
         * @param video the video being downloaded
         */
        private Job(VideoDetails video) {
            this.video = video;
        }

        /**
         * Returns the video this job downloads.
         *
         * @return the video details
         */
        public VideoDetails video() {
            return video;
        }

        /**
         * Returns the observable aggregated progress ({@code 0..1}).
         *
         * @return the progress property
         */
        public DoubleProperty progressProperty() {
            return progress;
        }

        /**
         * Returns the observable status text.
         *
         * @return the status property
         */
        public StringProperty statusProperty() {
            return status;
        }

        /**
         * Returns the observable job state.
         *
         * @return the state property
         */
        public ObjectProperty<State> stateProperty() {
            return state;
        }

        /**
         * Returns the output file of a finished job.
         *
         * @return the produced file, or {@code null} while running/failed
         */
        public Path output() {
            return output;
        }

        /**
         * Returns the failure cause of a failed job.
         *
         * @return the error, or {@code null}
         */
        public Throwable error() {
            return error;
        }

        /**
         * Publishes a progress value on the FX thread.
         *
         * @param value aggregated progress {@code 0..1}
         */
        private void progress(double value) {
            Context.update(() -> progress.set(Math.min(1.0, Math.max(0.0, value))));
        }

        /**
         * Publishes a status line on the FX thread.
         *
         * @param text the status text
         */
        private void status(String text) {
            Context.update(() -> status.set(text));
        }

        /**
         * Marks the job finished on the FX thread.
         *
         * @param out the produced file
         */
        private void finish(Path out) {
            this.output = out;
            Context.update(() -> {
                progress.set(1.0);
                status.set("Done — " + out.getFileName());
                state.set(State.DONE);
            });
        }

        /**
         * Marks the job failed on the FX thread.
         *
         * @param t the failure cause
         */
        private void fail(Throwable t) {
            this.error = t;
            Context.update(() -> {
                status.set("Failed — " + (t.getMessage() == null
                        ? t.getClass().getSimpleName() : t.getMessage()));
                state.set(State.FAILED);
            });
        }

        /**
         * Terminal states of a job.
         */
        public enum State {
            /**
             * The job is still downloading or muxing.
             */
            RUNNING,

            /**
             * The job produced its output file successfully.
             */
            DONE,

            /**
             * The job aborted with an error.
             */
            FAILED
        }
    }
}
