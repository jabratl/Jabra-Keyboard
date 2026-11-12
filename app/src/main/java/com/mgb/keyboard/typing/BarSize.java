package com.mgb.keyboard.typing;

/**
 * v0.16.6 (plan X, step X6): size of the toolbar (the row above the letters). Pure Java, no Android (tools/BarSizeTest.java).
 * Two percent prefs: tb_hpct = height, tb_ipct = icon / text size (and key width). 70..150, 100 = EXACTLY the old size
 * (36 dp high, 44 dp wide, text 12 sp, fold key 14 sp). Unset / broken value = 100. Rounding like SizePct (Math.round).
 */
public final class BarSize {
    private BarSize() { }

    public static final int MIN = 70, MAX = 150, STEP = 5, DEF = 100;
    public static final int BASE_H_DP = 36, BASE_W_DP = 44;
    public static final float BASE_SP = 12f, BASE_FOLD_SP = 14f;

    public static boolean valid(int pct) { return pct >= MIN && pct <= MAX; }

    /** Valid percent stays; anything else (unset -1, 0, 999, ...) = 100 = old size. */
    public static int sanitize(int pct) { return valid(pct) ? pct : DEF; }

    /** Key height in dp (100 = 36). */
    public static int heightDp(int pct) { return Math.round(BASE_H_DP * sanitize(pct) / 100f); }

    /** Key width in dp (100 = 44). Follows the icon / text size pref so bigger text still fits. */
    public static int widthDp(int pct) { return Math.round(BASE_W_DP * sanitize(pct) / 100f); }

    /** Text size in sp of a toolbar key (100 = 12 sp). */
    public static float textSp(int pct) { return BASE_SP * sanitize(pct) / 100f; }

    /** Text size in sp of the fold key (100 = 14 sp). */
    public static float foldSp(int pct) { return BASE_FOLD_SP * sanitize(pct) / 100f; }
}
