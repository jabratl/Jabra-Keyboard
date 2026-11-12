package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.15.2 (N2) pure helper: which clipboard group is shown first (pref clip_order, int).
 * 0 = Pinned first (default = the order before v0.15.2), 1 = Recent first. Any other value = 0.
 * Used by List and Cards view. Clip sort (clip_sort) still sorts INSIDE each group.
 */
public final class ClipOrder {
    private ClipOrder() {}

    public static final int PINNED_FIRST = 0, RECENT_FIRST = 1;
    public static final String[] NAMES = { "Pinned first", "Recent first" };

    public static int sanitize(int mode) { return mode == RECENT_FIRST ? RECENT_FIRST : PINNED_FIRST; }

    public static boolean recentFirst(int mode) { return sanitize(mode) == RECENT_FIRST; }

    /** Next value when the header is tapped (toggle). */
    public static int next(int mode) { return recentFirst(mode) ? PINNED_FIRST : RECENT_FIRST; }

    public static String name(int mode) { return NAMES[sanitize(mode)]; }

    /** Section labels in display order. */
    public static String[] labels(int mode) {
        return recentFirst(mode) ? new String[] { "Recent", "Pinned" } : new String[] { "Pinned", "Recent" };
    }

    /** The two groups in display order (a new list of 2 lists; null input = empty list). Items are not copied. */
    public static <T> List<List<T>> groups(List<T> pinned, List<T> recent, int mode) {
        List<T> p = pinned == null ? new ArrayList<T>() : pinned;
        List<T> r = recent == null ? new ArrayList<T>() : recent;
        List<List<T>> out = new ArrayList<>(2);
        if (recentFirst(mode)) { out.add(r); out.add(p); } else { out.add(p); out.add(r); }
        return out;
    }

    /** Headers are shown only when both groups have items and the user is not searching (same rule as before v0.15.2). */
    public static boolean showHeaders(boolean searching, int pinnedCount, int recentCount) {
        return !searching && pinnedCount > 0 && recentCount > 0;
    }
}
