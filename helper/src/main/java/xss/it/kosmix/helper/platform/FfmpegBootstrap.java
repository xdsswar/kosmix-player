package xss.it.kosmix.helper.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.helper.platform package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * First-run bootstrap of the Windows ffmpeg shared runtime.
 * <p>
 * skia-fx's media engine loads ffmpeg dynamically ({@code avcodec-61.dll}
 * and friends) and refuses any build whose avcodec major differs from the
 * headers it was compiled against (ffmpeg {@value #FFMPEG_VERSION}). This
 * helper downloads a pinned, ABI-matching shared build into a writable
 * per-user directory once, so the application can point the engine at it
 * from {@code Application.init()} via {@code Media.setFfmpegDirectory}.
 * <p>
 * Download candidates mirror the SDK's own fetch order:
 * <ol>
 *   <li>gyan.dev exact-release build (deterministic, preferred) — a .7z
 *       archive extracted by shelling out to {@code tar} (bsdtar, ships
 *       with Windows 10/11);</li>
 *   <li>BtbN release-branch build — a .zip extracted in pure Java.</li>
 * </ol>
 * The class is blocking by design; run it on a background executor.
 */
public final class FfmpegBootstrap {
    /**
     * The ffmpeg release the skia-fx media engine is pinned to. The
     * native loader rejects any avcodec whose major version differs
     * from this release's (avcodec-61 for the 7.1 line).
     */
    public static final String FFMPEG_VERSION = "7.1";

    /**
     * The avcodec DLL name of the pinned release, used as the "runtime
     * is present" probe file.
     */
    private static final String PROBE_DLL = "avcodec-61.dll";

    /**
     * Marker file stamped after a successful staging. Records which
     * archive produced the DLLs so a version bump invalidates the dir.
     */
    private static final String MARKER_FILE = "source.txt";

    /**
     * Download candidates, tried in order. The gyan.dev asset matches
     * the pinned release exactly; the BtbN asset tracks the release
     * branch (ABI-consistent with the same headers).
     */
    private static final String[] CANDIDATES = {
            "https://github.com/GyanD/codexffmpeg/releases/download/" + FFMPEG_VERSION
                    + "/ffmpeg-" + FFMPEG_VERSION + "-full_build-shared.7z",
            "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n"
                    + FFMPEG_VERSION + "-latest-win64-lgpl-shared.zip"
    };

    /**
     * Shared HTTP client used for the archive downloads. Redirects are
     * followed because GitHub release assets bounce through a CDN.
     */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Private constructor. This is a static utility holder and must
     * never be instantiated.
     */
    private FfmpegBootstrap() {
        throw new AssertionError("No instances of FfmpegBootstrap");
    }

    /**
     * Checks whether a previously staged, version-matching ffmpeg
     * runtime already exists in the target directory.
     *
     * @param targetDir the directory the DLLs are staged into
     * @return {@code true} when the runtime is ready to use
     */
    public static boolean isReady(Path targetDir) {
        final Path probe = targetDir.resolve(PROBE_DLL);
        final Path marker = targetDir.resolve(MARKER_FILE);
        try {
            return Files.exists(probe)
                    && Files.exists(marker)
                    && Files.readString(marker).contains("n" + FFMPEG_VERSION);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ensures the ffmpeg runtime is present in {@code targetDir},
     * downloading and staging it on first run. Blocking — run on a
     * background thread; safe to call repeatedly (idempotent).
     *
     * @param targetDir the writable directory to stage the DLLs into
     * @param status    optional progress sink for human-readable status
     *                  lines, may be {@code null}
     * @return the target directory, ready for
     *         {@code Media.setFfmpegDirectory(dir.toString())}
     * @throws IOException when no candidate could be fetched or staged
     */
    public static Path ensure(Path targetDir, Consumer<String> status) throws IOException {
        final Consumer<String> log = (status != null) ? status : s -> { };

        /*
         * Fast path: a matching runtime is already staged.
         */
        if (isReady(targetDir)) {
            log.accept("ffmpeg runtime already present");
            return targetDir;
        }

        Files.createDirectories(targetDir);
        final Path downloadDir = targetDir.resolve("download");
        Files.createDirectories(downloadDir);

        IOException lastError = null;
        for (String url : CANDIDATES) {
            final String name = url.substring(url.lastIndexOf('/') + 1);
            final Path archive = downloadDir.resolve(name);
            try {
                /*
                 * Download (resuming is unnecessary; the archives are
                 * < 100 MB and a partial file is simply re-fetched).
                 */
                if (!Files.exists(archive) || Files.size(archive) == 0) {
                    log.accept("Downloading " + name + " …");
                    download(url, archive);
                }

                /*
                 * Extract to a scratch dir, then stage only bin/*.dll.
                 */
                log.accept("Extracting " + name + " …");
                final Path scratch = downloadDir.resolve("extract");
                deleteRecursively(scratch);
                Files.createDirectories(scratch);
                if (name.endsWith(".zip")) {
                    unzip(archive, scratch);
                } else {
                    /*
                     * .7z — Windows 10/11 bsdtar reads 7-zip archives.
                     */
                    untar(archive, scratch);
                }

                final Path binDir = findBinDir(scratch).orElseThrow(() ->
                        new IOException("No bin/ directory with DLLs inside " + name));

                /*
                 * Clear stale DLLs first: leftovers from another ffmpeg
                 * branch would win the native loader probe and trip the
                 * ABI guard.
                 */
                clearDlls(targetDir);
                int copied = 0;
                try (DirectoryStream<Path> dlls = Files.newDirectoryStream(binDir, "*.dll")) {
                    for (Path dll : dlls) {
                        Files.copy(dll, targetDir.resolve(dll.getFileName().toString()),
                                StandardCopyOption.REPLACE_EXISTING);
                        copied++;
                    }
                }
                if (copied == 0) {
                    throw new IOException("Archive " + name + " contained no DLLs");
                }

                /*
                 * Stamp the flavor marker and clean up the scratch data.
                 */
                Files.writeString(targetDir.resolve(MARKER_FILE),
                        "n" + FFMPEG_VERSION + " <- " + name);
                deleteRecursively(scratch);
                Files.deleteIfExists(archive);
                log.accept("ffmpeg runtime ready (" + copied + " DLLs)");
                return targetDir;
            } catch (IOException e) {
                lastError = e;
                log.accept(name + " failed: " + e.getMessage());
                try {
                    Files.deleteIfExists(archive);
                } catch (IOException ignored) {
                    /*
                     * Best effort cleanup; the next attempt re-fetches.
                     */
                }
            }
        }
        throw (lastError != null)
                ? lastError
                : new IOException("No ffmpeg runtime candidate could be fetched");
    }

    /**
     * Streams the given URL to the destination file.
     *
     * @param url  the archive URL
     * @param dest the destination file
     * @throws IOException on any network or filesystem failure
     */
    private static void download(String url, Path dest) throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        try {
            final HttpResponse<InputStream> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            try (InputStream in = response.body()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Download interrupted: " + url);
        }
    }

    /**
     * Extracts a .zip archive in pure Java, guarding against zip-slip
     * path traversal.
     *
     * @param archive the zip file
     * @param dest    the directory to extract into
     * @throws IOException on a corrupt archive or filesystem failure
     */
    private static void unzip(Path archive, Path dest) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IOException("Blocked zip-slip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Extracts an archive by shelling out to {@code tar -xf} (bsdtar on
     * Windows 10/11, which also reads 7-zip archives).
     *
     * @param archive the archive file
     * @param dest    the directory to extract into
     * @throws IOException when tar is missing or exits non-zero
     */
    private static void untar(Path archive, Path dest) throws IOException {
        final ProcessBuilder builder = new ProcessBuilder(
                "tar", "-xf", archive.toAbsolutePath().toString(),
                "-C", dest.toAbsolutePath().toString()
        ).redirectErrorStream(true);
        try {
            final Process process = builder.start();
            /*
             * Drain the output so the process never blocks on a full pipe.
             */
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            final int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("tar extraction failed (exit " + exit + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Extraction interrupted");
        }
    }

    /**
     * Walks one or two levels below the scratch dir looking for a
     * {@code bin} directory that actually contains DLLs.
     *
     * @param scratch the extraction scratch directory
     * @return the bin directory when found
     * @throws IOException on a filesystem failure
     */
    private static Optional<Path> findBinDir(Path scratch) throws IOException {
        try (Stream<Path> walk = Files.walk(scratch, 3)) {
            return walk
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().equals("bin"))
                    .filter(p -> {
                        try (DirectoryStream<Path> dlls =
                                     Files.newDirectoryStream(p, "*.dll")) {
                            return dlls.iterator().hasNext();
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst();
        }
    }

    /**
     * Deletes every DLL in the target directory (stale-branch sweep).
     *
     * @param dir the directory to clean
     * @throws IOException on a filesystem failure
     */
    private static void clearDlls(Path dir) throws IOException {
        try (DirectoryStream<Path> dlls = Files.newDirectoryStream(dir, "*.dll")) {
            for (Path dll : dlls) {
                Files.deleteIfExists(dll);
            }
        }
    }

    /**
     * Recursively deletes a directory tree; missing paths are ignored.
     *
     * @param root the tree root
     * @throws IOException on a filesystem failure
     */
    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    /*
                     * Locked files are retried on the next bootstrap run.
                     */
                }
            });
        }
    }
}
