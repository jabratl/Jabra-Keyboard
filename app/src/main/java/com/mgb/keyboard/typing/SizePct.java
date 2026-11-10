package com.mgb.keyboard.typing;

/** v0.1.4 size percentages (pure Java, no Android). Height % of the 48 dp key; one-hand width % of the screen. -1 = not set = old behaviour. */
public final class SizePct {
    private SizePct() { }

    public static final int UNSET = -1;
    public static final int H_MIN = 70, H_MAX = 150, H_STEP = 5, H_BASE_DP = 48;
    public static final int W_MIN = 50, W_MAX = 95, W_STEP = 5, W_DEF_P = 82, W_DEF_L = 60;

    // v0.2.8: letter (alphabet) text size %. 100 = 14 sp = old size.
    public static final int L_MIN = 80, L_MAX = 220, L_STEP = 10, L_BASE_SP = 14;
    public static boolean letterValid(int pct) { return pct >= L_MIN && pct <= L_MAX; }
    /** Letter text size in sp for a percent; unset / invalid -> 14 sp (old size). */
    public static float letterSp(int pct) { return letterValid(pct) ? L_BASE_SP * pct / 100f : (float) L_BASE_SP; }

    // v0.3.0: gap between keys in dp (0..6, default 2 = old look).
    public static final int G_MIN = 0, G_MAX = 6, G_DEF = 2;
    public static boolean gapValid(int dp) { return dp >= G_MIN && dp <= G_MAX; }
    public static int gapDp(int dp) { return gapValid(dp) ? dp : G_DEF; }

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
