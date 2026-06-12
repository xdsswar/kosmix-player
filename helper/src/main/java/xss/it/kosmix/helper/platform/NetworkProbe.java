package xss.it.kosmix.helper.platform;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
 * Estimates the available download bandwidth by timing a bounded,
 * ranged fetch of a real media URL. Used by the player's "Auto"
 * quality mode to pick a resolution that the current network can
 * actually sustain. Blocking — run on a background thread; the most
 * recent estimate is cached briefly so repeated lookups are cheap.
 */
public final class NetworkProbe {
    /**
     * Number of bytes fetched for the throughput sample.
     */
    private static final int SAMPLE_BYTES = 1_500_000;

    /**
     * How long a measured estimate is considered fresh, in millis.
     */
    private static final long CACHE_TTL_MS = 60_000;

    /**
     * Shared HTTP client for probes (follows redirects, short connect).
     */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /**
     * Last measured throughput in megabits per second, {@code <= 0}
     * when nothing has been measured yet.
     */
    private static volatile double lastMbps = -1;

    /**
     * Wall-clock millis (from a caller-supplied stamp) of the last
     * measurement; used together with {@link #CACHE_TTL_MS}.
     */
    private static volatile long lastStampMs = 0;

    /**
     * Private constructor. This is a static utility holder and must
     * never be instantiated.
     */
    private NetworkProbe() {
        throw new AssertionError("No instances of NetworkProbe");
    }

    /**
     * Returns the most recent cached estimate without measuring.
     *
     * @return last measured Mbps, or {@code <= 0} when unknown
     */
    public static double cachedMbps() {
        return lastMbps;
    }

    /**
     * Measures download throughput by fetching a bounded byte range of
     * the given URL and timing it. Falls back to the cached value (or a
     * conservative estimate) on any failure.
     *
     * @param url        a media URL (typically the chosen video stream)
     * @param headers    optional HTTP headers (User-Agent, etc.), may be
     *                   {@code null}; supplied as alternating name/value
     * @param nowMs      the current wall-clock time in millis (the
     *                   caller provides it; scripts have no clock)
     * @return estimated throughput in megabits per second
     */
    public static double measureMbps(String url, String[] headers, long nowMs) {
        /*
         * Serve a fresh cached estimate to avoid hammering the CDN.
         */
        if (lastMbps > 0 && (nowMs - lastStampMs) < CACHE_TTL_MS) {
            return lastMbps;
        }
        try {
            final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Range", "bytes=0-" + (SAMPLE_BYTES - 1))
                    .GET();
            if (headers != null) {
                for (int i = 0; i + 1 < headers.length; i += 2) {
                    /*
                     * Skip Range; we set our own bounded range above.
                     */
                    if (!"Range".equalsIgnoreCase(headers[i])) {
                        builder.header(headers[i], headers[i + 1]);
                    }
                }
            }
            final long start = System.nanoTime();
            final HttpResponse<InputStream> response =
                    HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            long read = 0;
            try (InputStream in = response.body()) {
                final byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    read += n;
                    if (read >= SAMPLE_BYTES) {
                        break;
                    }
                }
            }
            final double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            if (seconds > 0 && read > 0) {
                /*
                 * bytes → bits → megabits, over the elapsed seconds.
                 */
                lastMbps = (read * 8.0 / 1_000_000.0) / seconds;
                lastStampMs = nowMs;
                return lastMbps;
            }
        } catch (Throwable ignored) {
            /*
             * Network hiccup: fall through to the cached/conservative
             * value below.
             */
        }
        return lastMbps > 0 ? lastMbps : 6.0;
    }

    /**
     * Maps an estimated throughput to a recommended maximum video
     * height, mirroring the rough bitrate needs of each tier.
     *
     * @param mbps the estimated throughput in megabits per second
     * @return the recommended height cap in pixels
     */
    public static int recommendedHeight(double mbps) {
        if (mbps >= 35) {
            return 2160;
        }
        if (mbps >= 16) {
            return 1440;
        }
        if (mbps >= 8) {
            return 1080;
        }
        if (mbps >= 4) {
            return 720;
        }
        if (mbps >= 2) {
            return 480;
        }
        return 360;
    }
}
