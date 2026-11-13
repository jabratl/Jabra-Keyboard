package com.mgb.keyboard.typing;

/**
 * v0.17.38 (M5): separate HEIGHT of the clip list in the Clipboard panel (Cards + List view, Classic AND New panel style).
 * Pure Java, no Android (tools/ClipListHTest.java). One percent pref: clip_list_pct, 50..100, 100 = EXACTLY the old height.
 * Only SHRINKS (design rule: the panel is never taller than the letters keyboard). Unset / broken value = 100.
 */
public final class ClipListH {
    private ClipListH() { }

    public static final int MIN = 50, MAX = 100, STEP = 10, DEF = 100;
    /** The list never gets lower than this (dp): one card row. */
    public static final int MIN_DP = 56;

    public static boolean valid(int pct) { return pct >= MIN && pct <= MAX; }

    /** Valid percent stays; anything else (unset -1, 0, 101 ...) = 100 = old height. */
    public static int sanitize(int pct) { return valid(pct) ? pct : DEF; }

    /** List height in px for the final (already fitted) height. 100 / bad = unchanged. Never below minPx (unless the old height was lower), never above old. */
    public static int heightPx(int curPx, int pct, int minPx) {
        int p = sanitize(pct);
        if (p == DEF || curPx <= 0) return curPx;
        int h = Math.round(curPx * p / 100f);
        int floor = Math.min(curPx, Math.max(minPx, 0));
        if (h < floor) h = floor;
        return h > curPx ? curPx : h;
    }
}
