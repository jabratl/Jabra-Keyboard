package com.mgb.keyboard.typing;

/**
 * v0.17.2 (K2, plan K): space bar size + swipe feel. Pure Java, no Android, tested by tools/SpacePlanTest.java.
 * Prefs (all default = exactly today): sp_wpct (space width %, 60..200, 100), sp_hpct (bottom row height %, 80..160, 100),
 * sp_anim (bool, the "< English >" label slides with the finger while swiping on the space bar, default ON).
 * Rule: 100 % returns the OLD number untouched (same code path as before the step).
 */
public final class SpacePlan {
    private SpacePlan() { }

    public static final int W_MIN = 60, W_MAX = 200, W_STEP = 10, W_DEF = 100;
    public static final int H_MIN = 80, H_MAX = 160, H_STEP = 10, H_DEF = 100;

    /** The space bar never gets lighter than this (weight units). */
    public static final float SPACE_FLOOR = 1.5f;
    /** The narrowest other key of the bottom row never gets narrower than this many dp when the space bar is widened (same value as plan K W_TOUCH). */
    public static final float MIN_KEY_DP = 24f;
    /** Label slide: at most this many dp, and at most a quarter of the space bar width. */
    public static final float SLIDE_MAX_DP = 48f;

    public static int sanitizeW(int pct) { return pct >= W_MIN && pct <= W_MAX ? pct : W_DEF; }
    public static int sanitizeH(int pct) { return pct >= H_MIN && pct <= H_MAX ? pct : H_DEF; }

    /** True when the old code path must run unchanged. */
    public static boolean isDefault(int wPct, int hPct) { return sanitizeW(wPct) == W_DEF && sanitizeH(hPct) == H_DEF; }

    /**
     * Space bar weight after the user's width %.
     * base = the weight the old rules computed (BottomPlan.spaceWeight / spaceOld); othersSum = weights of all other keys in the row;
     * minOther = the smallest weight among the other keys; rowDp = real row width in dp.
     * 100 % = base (exact). Wider: capped so the narrowest other key stays >= MIN_KEY_DP, never below base. Narrower: never below SPACE_FLOOR, never above base.
     * Bad numbers (NaN, 0, negative) = base.
     */
    public static float spaceWeight(float base, int wPct, float othersSum, float minOther, float rowDp) {
        int pct = sanitizeW(wPct);
        if (pct == W_DEF || !(base > 0f)) return base;
        float w = base * pct / 100f;
        if (pct < W_DEF) {
            float lo = Math.min(SPACE_FLOOR, base);
            return w < lo ? lo : w;
        }
        if (othersSum > 0f && minOther > 0f && rowDp > 0f) {
            float hi = minOther * rowDp / MIN_KEY_DP - othersSum;   // total weight allowed = minOther * rowDp / MIN_KEY_DP
            if (hi < base) hi = base;
            if (w > hi) w = hi;
        }
        return w < base ? base : w;
    }

    /** Bottom row key height in dp: key height x percent. 100 % = keyDp exactly. */
    public static int heightDp(int keyDp, int hPct) {
        int pct = sanitizeH(hPct);
        if (pct == H_DEF) return keyDp;
        int v = Math.round(keyDp * pct / 100f);
        return v < 1 ? 1 : v;
    }

    /** Swipe progress -1..1 = finger travel / one language step (clamped). Bad numbers = 0. */
    public static float progress(float dxPx, float stepPx) {
        if (!(stepPx > 0f) || Float.isNaN(dxPx) || Float.isInfinite(dxPx)) return 0f;
        float p = dxPx / stepPx;
        return p > 1f ? 1f : (p < -1f ? -1f : p);
    }

    /** Label shift in px for a progress -1..1: at most SLIDE_MAX_DP and at most a quarter of the bar. */
    public static float slidePx(float progress, float barWidthPx, float density) {
        if (Float.isNaN(progress) || !(barWidthPx > 0f) || !(density > 0f)) return 0f;
        float p = progress > 1f ? 1f : (progress < -1f ? -1f : progress);
        float max = Math.min(SLIDE_MAX_DP * density, barWidthPx / 4f);
        return p * max;
    }

    /** The slide is drawn only with the space swipe ON, sp_anim ON and system animations not switched off. */
    public static boolean animOn(boolean spaceLang, boolean spAnim, float systemAnimScale) {
        return spaceLang && spAnim && systemAnimScale > 0f;
    }

    /** A swipe that travelled one language step or more never types a space when the finger lifts. */
    public static boolean cancelsTap(float travelPx, float stepPx) {
        return stepPx > 0f && travelPx >= stepPx;
    }
}
