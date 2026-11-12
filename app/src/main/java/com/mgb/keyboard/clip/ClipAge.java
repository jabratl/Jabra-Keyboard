package com.mgb.keyboard.clip;

/**
 * v0.10.24 (C1) short age text for a clip: "now", "5m", "3h", "2d", "3w", then "" (older than ~8 weeks, or time unknown). Pure Java; tested by tools/ClipKindTest.java.
 */
public final class ClipAge {
    private ClipAge() { }

    public static String shortAge(long madeMs, long nowMs) {
        if (madeMs <= 0L || nowMs <= 0L) return "";
        long d = nowMs - madeMs;
        if (d < 0L) return "now";
        long min = d / 60000L;
        if (min < 1L) return "now";
        if (min < 60L) return min + "m";
        long h = min / 60L;
        if (h < 24L) return h + "h";
        long days = h / 24L;
        if (days < 7L) return days + "d";
        long w = days / 7L;
        if (w <= 8L) return w + "w";
        return "";
    }
}
