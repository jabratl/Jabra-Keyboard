package com.mgb.keyboard.typing;

/** v0.1.4 size percentages (pure Java, no Android). Height % of the 48 dp key; one-hand width % of the screen. -1 = not set = old behaviour. */
public final class SizePct {
    private SizePct() { }

    public static final int UNSET = -1;
    public static final int H_MIN = 70, H_MAX = 150, H_STEP = 5, H_BASE_DP = 48;
    public static final int W_MIN = 50, W_MAX = 95, W_STEP = 5, W_DEF_P = 82, W_DEF_L = 60;

    public static boolean heightValid(int pct) { return pct >= H_MIN && pct <= H_MAX; }
    public static boolean widthValid(int pct) { return pct >= W_MIN && pct <= W_MAX; }

    /** Key height in dp for a valid height percent (100 = 48 dp = old Medium). */
    public static int heightDp(int pct) { return Math.round(H_BASE_DP * clamp(pct, H_MIN, H_MAX) / 100f); }

    /** Percent that matches an old dp value (40/48/56/64 dp -> 83/100/117/133). */
    public static int pctFromDp(int dp) { return Math.round(dp * 100f / H_BASE_DP); }

    /** One-hand width as a fraction of the screen width; unset / invalid -> old value (0.82 portrait, 0.60 landscape). */
    public static float widthFrac(int pct, boolean land) {
        if (widthValid(pct)) return pct / 100f;
        return (land ? W_DEF_L : W_DEF_P) / 100f;
    }

    /** Next value for a - / + tap: snaps to a multiple of [step], stays in [min, max]. dir = -1 or +1. */
    public static int step(int cur, int dir, int min, int max, int step) {
        int v = cur;
        if (dir > 0) v = (cur / step + 1) * step;
        else if (dir < 0) v = ((cur + step - 1) / step - 1) * step;
        return clamp(v, min, max);
    }

    static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
