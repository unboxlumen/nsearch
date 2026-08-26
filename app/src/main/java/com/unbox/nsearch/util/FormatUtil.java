package com.unbox.nsearch.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 界面展示用的格式化工具。
 */
public final class FormatUtil {

    /** 界面里各项信息之间的统一分隔符（单空格 · 单空格）。 */
    public static final String SEPARATOR = " · ";

    /** 线程安全（{@link DateTimeFormatter} 不可变），可安全地被任意线程并发调用。 */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault());

    private FormatUtil() {
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = -1;
        do {
            v /= 1024;
            i++;
        } while (i < units.length - 1 && v >= 1024);
        return String.format(Locale.getDefault(), "%.1f %s", v, units[i]);
    }

    public static String formatDate(long millis) {
        if (millis <= 0) return "";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_FMT);
    }

    public static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        long s = ms / 1000;
        if (s < 60) return s + "s";
        long m = s / 60;
        return m + "m" + (s % 60) + "s";
    }
}
