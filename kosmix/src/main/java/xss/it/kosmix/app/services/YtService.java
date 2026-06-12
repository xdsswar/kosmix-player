package xss.it.kosmix.app.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import xss.it.YouTube;
import xss.it.YouTubeOptions;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.app.K;
import xss.it.kosmix.helper.platform.NetworkProbe;
import xss.it.model.Format;
import xss.it.model.Page;
import xss.it.model.VideoDetails;
import xss.it.model.VideoInfo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

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
 * Asynchronous facade over the blocking ytnfx {@link YouTube} client.
 * Every call runs on the shared application executor and delivers its
 * result (or error) back on the FX Application Thread. The underlying
 * client is rebuilt whenever the user changes the extraction client or
 * the custom User-Agent in the settings, and video details are cached
 * in a small LRU so hover previews and playback share lookups.
 */
public final class YtService implements AutoCloseable {
    /**
     * Maximum number of {@link VideoDetails} kept in the LRU cache.
     */
    private static final int CACHE_SIZE = 64;

    /**
     * The application context (settings, executor).
     */
    private final Context context;

    /**
     * The active ytnfx client. Replaced atomically by {@link #rebuild()};
     * never {@code null} after construction.
     */
    private volatile YouTube yt;

    /**
     * LRU cache of video details keyed by video id. Guarded by its own
     * monitor because lookups happen from background threads.
     */
    private final Map<String, VideoDetails> detailsCache =
            new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
                /**
                 * Evicts the eldest entry once the cache exceeds its
                 * configured capacity.
                 *
                 * @param eldest the eldest cache entry
                 * @return {@code true} when the entry must be removed
                 */
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, VideoDetails> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    /**
     * Creates the service and builds the initial ytnfx client from the
     * current settings.
     *
     * @param context the application context
     */
    public YtService(Context context) {
        this.context = context;
        rebuild();
    }

    /**
     * (Re)builds the ytnfx client from the persisted settings. Called
     * at construction and whenever the settings page applies changes.
     * The previous client is closed to release its GraalJS context.
     */
    public synchronized void rebuild() {
        final String client = context.settings().get(K.CLIENT, K.DEFAULT_CLIENT);
        final String userAgent = context.settings().get(K.USER_AGENT, "");

        final YouTubeOptions.Builder builder = YouTubeOptions.builder()
                .language("en")
                .enrichChannelThumbnail(true);
        /*
         * "auto" keeps ytnfx's full default client chain
         * (web_safari → android_vr), which matters for stream quality:
         * the fallback client is the one that yields the complete set
         * of high-resolution adaptive formats. A concrete client pins
         * extraction to that client only.
         */
        if (!K.AUTO_CLIENT.equalsIgnoreCase(client)) {
            builder.videoClients(List.of(client));
        }
        /*
         * A custom User-Agent is applied as a client override; version
         * stays null so ytnfx keeps the client's own version string.
         */
        if (!userAgent.isBlank()) {
            builder.clientOverride(
                    K.AUTO_CLIENT.equalsIgnoreCase(client) ? "web_safari" : client,
                    null, userAgent);
        }

        final YouTube fresh = new YouTube(builder.build());
        final YouTube old = this.yt;
        this.yt = fresh;
        if (old != null) {
            try {
                old.close();
            } catch (Exception ignored) {
                /*
                 * Closing an already-dead client is harmless.
                 */
            }
        }
    }

    /**
     * Searches YouTube asynchronously.
     *
     * @param query the search query
     * @param token continuation token from a previous page, or
     *              {@code null} for the first page
     * @param ok    FX-thread consumer of the result page
     * @param err   FX-thread consumer of any failure
     */
    public void search(String query, String token,
                       Consumer<Page<VideoInfo>> ok, Consumer<Throwable> err) {
        context.executor().execute(() -> {
            try {
                final Page<VideoInfo> page = (token == null)
                        ? yt.search(query)
                        : yt.search(query, token);
                Context.update(() -> ok.accept(page));
            } catch (Throwable t) {
                Context.update(() -> err.accept(t));
            }
        });
    }

    /**
     * Fetches full video details asynchronously, serving repeated
     * lookups from the LRU cache.
     *
     * @param info the listing entry to resolve
     * @param ok   FX-thread consumer of the details
     * @param err  FX-thread consumer of any failure
     */
    public void details(VideoInfo info, Consumer<VideoDetails> ok, Consumer<Throwable> err) {
        /*
         * Fast path: cache hit needs no background hop.
         */
        final VideoDetails cached = cachedDetails(info.id());
        if (cached != null) {
            ok.accept(cached);
            return;
        }
        context.executor().execute(() -> {
            try {
                final VideoDetails details = yt.getVideo(info);
                synchronized (detailsCache) {
                    detailsCache.put(info.id(), details);
                }
                Context.update(() -> ok.accept(details));
            } catch (Throwable t) {
                Context.update(() -> err.accept(t));
            }
        });
    }

    /**
     * Shared HTTP client for the lightweight suggestion endpoint.
     */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Fetches search suggestions for a partial query (the same
     * endpoint the YouTube search box uses). Failures are silent — the
     * search box simply shows no suggestions.
     *
     * @param query the partial query, must not be blank
     * @param ok    FX-thread consumer of the suggestion list
     */
    public void suggest(String query, Consumer<List<String>> ok) {
        context.executor().execute(() -> {
            try {
                final String url = "https://suggestqueries.google.com/complete/search"
                        + "?client=firefox&ds=yt&q="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8);
                final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();
                final HttpResponse<String> response =
                        HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return;
                }
                /*
                 * Response shape: ["query", ["sugg1", "sugg2", ...], ...]
                 */
                final JsonArray root = JsonParser.parseString(response.body()).getAsJsonArray();
                final JsonArray values = root.get(1).getAsJsonArray();
                final List<String> suggestions = new ArrayList<>(values.size());
                values.forEach(v -> suggestions.add(v.getAsString()));
                Context.update(() -> ok.accept(suggestions));
            } catch (Throwable ignored) {
                /*
                 * Suggestions are best-effort sugar; never surface errors.
                 */
            }
        });
    }

    /**
     * Returns the cached details for a video id when present.
     *
     * @param id the video id
     * @return the cached details or {@code null}
     */
    public VideoDetails cachedDetails(String id) {
        synchronized (detailsCache) {
            return detailsCache.get(id);
        }
    }

    /**
     * Picks the video-only stream honoring the configured quality mode
     * (settings): a fixed height, the best available, or — in Auto —
     * the best the measured network bandwidth can sustain.
     *
     * @param details the resolved video details
     * @return the chosen video-only format when one exists
     */
    public Optional<Format> pickVideo(VideoDetails details) {
        return bestVideo(details, resolveCap(details));
    }

    /**
     * Resolves the height cap to apply for the current quality mode.
     * In Auto mode this probes the network throughput (cached briefly)
     * against the chosen video stream and maps it to a resolution tier.
     *
     * @param details the resolved video details
     * @return the height cap in pixels, 0 = unlimited
     */
    private int resolveCap(VideoDetails details) {
        final String mode = context.settings().get(K.QUALITY, K.DEFAULT_QUALITY);
        if (K.QUALITY_BEST.equalsIgnoreCase(mode)) {
            return 0;
        }
        if (!K.QUALITY_AUTO.equalsIgnoreCase(mode)) {
            try {
                return Integer.parseInt(mode.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        /*
         * Auto: measure throughput against the best stream's URL and
         * map it to a tier. The probe is cached, so this is cheap after
         * the first call within a minute.
         */
        final Optional<Format> probeFmt = bestVideo(details, 0);
        if (probeFmt.isEmpty()) {
            return 0;
        }
        final Format f = probeFmt.get();
        final String[] headers = flattenHeaders(f);
        final double mbps = NetworkProbe.measureMbps(f.url(), headers, System.currentTimeMillis());
        return NetworkProbe.recommendedHeight(mbps);
    }

    /**
     * Flattens a format's HTTP headers into an alternating name/value
     * array for the network probe.
     *
     * @param format the format whose headers to flatten
     * @return alternating name/value pairs, or an empty array
     */
    private static String[] flattenHeaders(Format format) {
        final Map<String, String> map = format.httpHeaders();
        if (map == null || map.isEmpty()) {
            return new String[0];
        }
        final String[] flat = new String[map.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> e : map.entrySet()) {
            flat[i++] = e.getKey();
            flat[i++] = e.getValue();
        }
        return flat;
    }

    /**
     * Picks the best video-only stream for the dual-source player:
     * highest resolution, then frame rate, then bitrate, restricted to
     * plain progressive HTTPS formats without DRM.
     *
     * @param details the resolved video details
     * @return the best video-only format when one exists
     */
    public static Optional<Format> bestVideo(VideoDetails details) {
        return bestVideo(details, 0);
    }

    /**
     * Picks the best video-only stream up to an optional height cap.
     *
     * @param details   the resolved video details
     * @param maxHeight maximum allowed height in pixels, 0 = unlimited
     * @return the best matching video-only format when one exists
     */
    public static Optional<Format> bestVideo(VideoDetails details, int maxHeight) {
        return details.formats().stream()
                .filter(Format::isVideoOnly)
                .filter(f -> !f.hasDrm())
                .filter(f -> "https".equals(f.protocol()))
                .filter(f -> maxHeight <= 0
                        || f.height() == null
                        || f.height() <= maxHeight)
                .max(Comparator
                        .comparing((Format f) -> f.height() == null ? 0 : f.height())
                        .thenComparing(f -> f.fps() == null ? 0 : f.fps())
                        .thenComparing(f -> f.bitrateKbps() == null ? 0 : f.bitrateKbps()));
    }

    /**
     * Picks the best audio-only stream (highest bitrate, plain HTTPS,
     * no DRM) to pair with {@link #bestVideo(VideoDetails)}.
     *
     * @param details the resolved video details
     * @return the best audio-only format when one exists
     */
    public static Optional<Format> bestAudio(VideoDetails details) {
        return details.formats().stream()
                .filter(Format::isAudioOnly)
                .filter(f -> !f.hasDrm())
                .filter(f -> "https".equals(f.protocol()))
                .max(Comparator.comparing(f -> f.bitrateKbps() == null ? 0 : f.bitrateKbps()));
    }

    /**
     * Picks the best muxed (audio+video single file) stream — the
     * fallback when a dual-source pair is unavailable.
     *
     * @param details the resolved video details
     * @return the best muxed format when one exists
     */
    public static Optional<Format> bestMuxed(VideoDetails details) {
        return details.formats().stream()
                .filter(Format::isMuxed)
                .filter(f -> !f.hasDrm())
                .filter(f -> "https".equals(f.protocol()))
                .max(Comparator
                        .comparing((Format f) -> f.height() == null ? 0 : f.height())
                        .thenComparing(f -> f.bitrateKbps() == null ? 0 : f.bitrateKbps()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            yt.close();
        } catch (Exception ignored) {
            /*
             * Shutdown path; nothing useful to do with the failure.
             */
        }
    }
}
