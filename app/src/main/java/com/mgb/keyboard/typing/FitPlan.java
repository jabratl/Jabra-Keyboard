package com.mgb.keyboard.typing;

/**
 * v0.10.21 (D4) SCREEN SIZE + FONT SCALE rules. Pure Java (no Android), tested by tools/FitPlanTest.java.
 * Phone at font scale 1.0 (and up to 1.15) and widths up to 700dp: every function returns the INPUT unchanged = identical to before.
 */
public final class FitPlan {
    private FitPlan() { }

    /** Panel text may grow with the phone's font size only up to this factor (panel buttons have fixed dp heights). */
    public static final float FONT_CAP = 1.15f;
    /** Windows wider than this (tablet / fold / split screen) get a centred content column. */
    public static final int WIDE_DP = 700;
    /** Width of that column. */
    public static final int COLUMN_DP = 600;

    /** True when the font scale is above the cap (bad / zero / NaN scale = false). */
    public static boolean needsCap(float fontScale) {
        return fontScale == fontScale && fontScale > FONT_CAP && fontScale < 100f;
    }

    /**
     * sp value to pass to setTextSize(SP, x) so that the final size is at most sp * FONT_CAP:
     * x * fontScale = sp * FONT_CAP  ->  x = sp * FONT_CAP / fontScale. At or below the cap the sp is returned unchanged.
     */
    public static float cappedSp(float sp, float fontScale) {
        if (!needsCap(fontScale) || sp <= 0f) return sp;
        return sp * FONT_CAP / fontScale;
    }

    /** True when the content should be limited to a centred column (screen width in dp > WIDE_DP). */
    public static boolean shouldWrap(int screenWidthDp) { return screenWidthDp > WIDE_DP; }

    /** Width in dp the panel content should have: the screen width, or COLUMN_DP on wide screens. Never below 1. */
    public static int maxContentWidthDp(int screenWidthDp) {
        if (screenWidthDp <= 0) return 1;
        return shouldWrap(screenWidthDp) ? COLUMN_DP : screenWidthDp;
    }

    /** Integer clamp (lo wins when lo > hi). */
    public static int clamp(int v, int lo, int hi) {
        if (hi < lo) return lo;
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Left edge for a box of width w inside a screen of width sw (keeps it fully on screen; too wide = 0). */
    public static int clampLeft(int x, int w, int sw) {
        if (w >= sw) return 0;
        return clamp(x, 0, sw - w);
    }
}
