package com.mgb.keyboard.typing;

/**
 * v0.9.6 (plan V1) reference-style key bubble numbers (pure Java, no Android, tested by tools/PopupStyleTest.java).
 * Bubble = rounded (14 dp), key width + 14 dp, about 1.35 x key height, big letter, filled with the theme key colour
 * (a little lighter). Classic theme (no key colour) stays white with dark text. x / y reuse TipPlace.
 * Also holds the "who may hide the bubble" rule (fast typing: finger A lifts while finger B already shows its bubble).
 */
public final class PopupStyle {
    private PopupStyle() { }

    public static final float EXTRA_W_DP = 14f;
    public static final float MIN_W_DP = 44f;
    public static final float HEIGHT_K = 1.35f;
    public static final float MIN_H_DP = 56f;
    public static final float RADIUS_DP = 14f;
    public static final float GAP_DP = 4f;
    public static final float TEXT_MIN_SP = 24f;
    public static final float TEXT_MAX_SP = 32f;
    public static final float LIGHTEN = 0.10f;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int DARK_TEXT = 0xFF222222;
    public static final int LIGHT_TEXT = 0xFFF2F2F2;
    public static final int CLASSIC_STROKE = 0xFF888888;

    /** Bubble width in px: key width + 14 dp, never below 44 dp. */
    public static int width(int keyW, float d) {
        int w = Math.round(keyW + EXTRA_W_DP * d);
        int min = Math.round(MIN_W_DP * d);
        return w < min ? min : w;
    }

    /** Bubble height in px: 1.35 x key height, never below 56 dp. */
    public static int height(int keyH, float d) {
        int h = Math.round(keyH * HEIGHT_K);
        int min = Math.round(MIN_H_DP * d);
        return h < min ? min : h;
    }

    /** Corner radius in px. */
    public static float radiusPx(float d) { return RADIUS_DP * d; }

    /** Letter size in sp: 0.6 x key height (dp), kept between 24 and 32. */
    public static float textSp(int keyH, float d) {
        float dp = d <= 0f ? 0f : keyH / d;
        float sp = dp * 0.6f;
        return sp < TEXT_MIN_SP ? TEXT_MIN_SP : (sp > TEXT_MAX_SP ? TEXT_MAX_SP : sp);
    }

    /** Lighter colour: each RGB channel moves f towards 255, alpha forced opaque. f clamped 0..1. */
    public static int lighten(int argb, float f) {
        float k = f < 0f ? 0f : (f > 1f ? 1f : f);
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r = Math.round(r + (255 - r) * k);
        g = Math.round(g + (255 - g) * k);
        b = Math.round(b + (255 - b) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Brightness 0..1 (simple weights, alpha ignored). */
    public static float lum(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
    }

    /** Bubble fill: theme key colour lightened by 0.10; Classic (null key colour) = white. Always opaque. */
    public static int fill(Integer themeKey) {
        if (themeKey == null) return WHITE;
        return lighten(themeKey.intValue(), LIGHTEN);
    }

    /** Letter colour: the theme text colour when it is readable on [fill] (brightness gap >= 0.35), else dark / light by the fill. */
    public static int text(Integer themeText, int fill) {
        if (themeText != null) {
            int t = 0xFF000000 | themeText.intValue();
            if (Math.abs(lum(t) - lum(fill)) >= 0.35f) return t;
        }
        return lum(fill) > 0.5f ? DARK_TEXT : LIGHT_TEXT;
    }

    /** Thin outline: Classic keeps the grey line of v0.9.5, themed = fill darkened 25 %. */
    public static int stroke(Integer themeKey, int fill) {
        return themeKey == null ? CLASSIC_STROKE : LookColors.darken(fill, 0.25f);
    }

    /** Left edge (centred over the key, kept on screen). */
    public static int x(int keyLeft, int keyW, int w, int screenW) { return TipPlace.x(keyLeft, keyW, w, screenW); }

    /** Top edge (4 dp above the key, never above the screen top). */
    public static int y(int keyTop, int h, float d) { return TipPlace.y(keyTop, h, Math.round(GAP_DP * d)); }

    /**
     * Who may hide the bubble on finger-up / cancel: nobody owns it (null) or the same key that owns it.
     * Fast typing: key A still down, key B pressed (B owns the bubble), A lifts -> A must NOT hide B's bubble.
     */
    public static boolean releaseHides(Object owner, Object releasedKey) {
        return owner == null || owner == releasedKey;
    }
}
