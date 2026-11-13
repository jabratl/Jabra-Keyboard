package com.mgb.keyboard.typing;

/**
 * v0.17.33 (UI9): size of the clip CARDS in the Clipboard panel (Cards view, Classic AND New panel style).
 * Pure Java, no Android (tools/ClipCardSizeTest.java). One percent pref: clip_card_pct, 70..140, 100 = EXACTLY the old size.
 * Unset / broken value = 100. The percent scales the clip text size and the card minimum height. The number of text lines
 * (pref clip_lines) is NOT touched. Smaller cards = more cards / more text visible; bigger cards = easier to read.
 */
public final class ClipCardSize {
    private ClipCardSize() { }

    public static final int MIN = 70, MAX = 140, STEP = 10, DEF = 100;
    /** A card never gets lower than this (the pin / x bar is 30 dp, plus 2 text lines). */
    public static final int MIN_CARD_DP = 56;
    /** The clip text never gets smaller than this (sp). */
    public static final float MIN_SP = 9f;

    public static boolean valid(int pct) { return pct >= MIN && pct <= MAX; }

    /** Valid percent stays; anything else (unset -1, 0, 999 ...) = 100 = old size. */
    public static int sanitize(int pct) { return valid(pct) ? pct : DEF; }

    /** Card minimum height in dp for the old dp height. 100 = unchanged. Never below MIN_CARD_DP (unless the old height was already lower). */
    public static int minHeightDp(int oldDp, int pct) {
        int p = sanitize(pct);
        if (p == DEF || oldDp <= 0) return oldDp;
        int h = Math.round(oldDp * p / 100f);
        int floor = Math.min(oldDp, MIN_CARD_DP);
        return h < floor ? floor : h;
    }

    /** Clip text size scaled by the percent. 100 = unchanged. Never below MIN_SP (unless the old size was already lower). */
    public static float textSize(float old, int pct) {
        int p = sanitize(pct);
        if (p == DEF) return old;
        float s = old * p / 100f;
        float floor = Math.min(old, MIN_SP);
        return s < floor ? floor : s;
    }
}
