package xss.it.kosmix.helper.utils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.helper.utils package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * Formatting helpers for the UI: compact view counts ("1.2M views"),
 * durations ("12:34", "1:02:03") and byte sizes ("231.4 MB").
 */
public final class Strings {
    /**
     * Private constructor. This is a static utility holder and must
     * never be instantiated.
     */
    private Strings() {
        throw new AssertionError("No instances of Strings");
    }

    /**
     * Formats a raw view count the way YouTube does: compact units with
     * one decimal under 100 of a unit ("1.2M", "12K", "987").
     *
     * @param count the raw count, may be {@code null}
     * @return the compact human string, empty when count is null
     */
    public static String compactCount(Long count) {
        if (count == null) {
            return "";
        }
        final double c = count.doubleValue();
        if (c >= 1_000_000_000d) {
            return trimDecimal(c / 1_000_000_000d) + "B";
        }
        if (c >= 1_000_000d) {
            return trimDecimal(c / 1_000_000d) + "M";
        }
        if (c >= 1_000d) {
            return trimDecimal(c / 1_000d) + "K";
        }
        return String.valueOf(count);
    }

    /**
     * Formats a full view count with thousands separators
     * ("2,184,031 views" style, without the suffix).
     *
     * @param count the raw count, may be {@code null}
     * @return the grouped number, empty when count is null
     */
    public static String groupedCount(Long count) {
        if (count == null) {
            return "";
        }
        return NumberFormat.getIntegerInstance(Locale.US).format(count);
    }

    /**
     * Formats a duration in seconds as a YouTube badge string:
     * {@code M:SS} below one hour, {@code H:MM:SS} above.
     *
     * @param seconds the duration in seconds, may be {@code null}
     * @return the formatted duration, empty when seconds is null
     */
    public static String duration(Long seconds) {
        if (seconds == null || seconds < 0) {
            return "";
        }
        final long h = seconds / 3600;
        final long m = (seconds % 3600) / 60;
        final long s = seconds % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%d:%02d", m, s);
    }

    /**
     * Formats a byte count using binary-ish friendly units
     * ("231.4 MB", "1.2 GB").
     *
     * @param bytes the byte count
     * @return the formatted size string
     */
    public static String bytes(long bytes) {
        if (bytes >= 1_073_741_824L) {
            return String.format("%.1f GB", bytes / 1_073_741_824d);
        }
        if (bytes >= 1_048_576L) {
            return String.format("%.1f MB", bytes / 1_048_576d);
        }
        if (bytes >= 1_024L) {
            return String.format("%.1f KB", bytes / 1_024d);
        }
        return bytes + " B";
    }

    /**
     * Renders a fractional value with one decimal, dropping the decimal
     * when it is zero ("1.0" → "1", "1.2" → "1.2").
     *
     * @param value the value to render
     * @return the trimmed string
     */
    private static String trimDecimal(double value) {
        final String s = String.format(Locale.US, "%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
