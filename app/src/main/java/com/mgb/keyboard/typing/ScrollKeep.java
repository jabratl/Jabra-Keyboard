package com.mgb.keyboard.typing;

/**
 * v0.16.1 (X1): keep the scroll position of a list when its panel is rebuilt (pure Java, no Android).
 * A panel rebuild creates a NEW ScrollView at position 0; the panel remembers the old scrollY and restores
 * clamp(saved, contentHeight, viewHeight) after the first layout. Nothing is stored.
 */
public final class ScrollKeep {
    private ScrollKeep() { }

    /** Nothing saved (start at the top). */
    public static final int NONE = -1;

    /** true when a saved position should be restored (saved > 0; NONE / 0 / negative = stay at the top). */
    public static boolean wants(int saved) { return saved > 0; }

    /**
     * Position to scroll to: saved, but never below 0 and never past the end of the content
     * (the list may be shorter now, e.g. after a delete). Unknown sizes (<= 0) give 0. Overflow safe.
     */
    public static int clamp(int saved, int contentH, int viewH) {
        if (saved <= 0 || contentH <= 0 || viewH < 0) return 0;
        long max = (long) contentH - (long) viewH;
        if (max <= 0) return 0;
        return (int) Math.min((long) saved, max);
    }
}
