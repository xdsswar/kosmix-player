package xss.it.kosmix.helper.platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
 * Static platform utilities: well-known directories, separators, CPU
 * topology and JVM capability probes. Everything here is side-effect
 * free except {@link #createDirs(String, String)}.
 */
public final class Platform {
    /**
     * Private constructor. This is a static utility holder and must
     * never be instantiated.
     */
    private Platform() {
        throw new AssertionError("No instances of Platform");
    }

    /**
     * Returns the per-user application data directory used as the base
     * for the hidden configuration folder.
     * <p>
     * On Windows this resolves to {@code %APPDATA%} (roaming profile);
     * when the variable is missing it falls back to the user home
     * directory so the application always has a writable location.
     *
     * @return the absolute path of the data directory, ending without a
     *         trailing separator
     */
    public static String getProgramDataDir() {
        /*
         * Prefer the roaming application-data folder; it is writable for
         * the current user and survives OS re-installs of the app.
         */
        final String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return appData;
        }
        return System.getProperty("user.home");
    }

    /**
     * Returns the platform specific directory separator.
     *
     * @return the separator string (e.g. {@code \} on Windows)
     */
    public static String dirSeparator() {
        return File.separator;
    }

    /**
     * Ensures that {@code parent/folder} exists, creating the full
     * directory chain when missing.
     *
     * @param parent the parent directory (must be writable)
     * @param folder the child folder name to create inside the parent
     * @throws IOException if the directories cannot be created
     */
    public static void createDirs(String parent, String folder) throws IOException {
        final Path path = Paths.get(parent, folder);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    /**
     * Returns the number of logical CPU cores visible to the JVM.
     *
     * @return the available processor count, always {@code >= 1}
     */
    public static int getCpuCores() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Probes whether the running JVM supports virtual threads.
     * <p>
     * Virtual threads are final since Java 21, so on the Java 25
     * baseline of this project the probe is effectively always
     * {@code true}; it is kept as a defensive runtime check so the
     * thread pool can degrade gracefully on exotic runtimes.
     *
     * @return {@code true} when virtual threads can be created
     */
    public static boolean supportsVirtualThreads() {
        try {
            /*
             * Creating an unstarted virtual thread is cheap and throws
             * on runtimes that do not support them.
             */
            Thread.ofVirtual().unstarted(() -> { });
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
