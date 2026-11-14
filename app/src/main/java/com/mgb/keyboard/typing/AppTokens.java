package com.mgb.keyboard.typing;

/**
 * v0.14.0 (M4 of the "App UI" plan): the numbers of the NEW app screen in one place (pure Java, no Android).
 * Spacing / touch size / radii = the values the screen already used (no visual change), motion = new (short fades).
 * Motion rule: system "Animator duration scale" 0 (animations off) = no motion at all; slow-motion settings are capped.
 */
public final class AppTokens {
    private AppTokens() { }

    // ---- spacing (dp) ----
    public static final int S1 = 4;
    public static final int S2 = 8;
    public static final int S3 = 12;
    public static final int S4 = 16;
    // ---- touch target (dp) ----
    public static final int TOUCH = 48;
    // ---- v0.17.76 (U1): type scale (sp) - the ONLY sizes the app screens may use. Old literals map: 12 help, 13 label, 14 body, 15 row title, 16 head ----
    public static final int T_CAPTION = 12, T_LABEL = 13, T_BODY = 14, T_ROW = 15, T_TITLE = 16, T_HEAD = 20, T_DISPLAY = 24;
    // ---- v0.17.76 (U1): shape (dp) ----
    public static final int R_BTN = 12, R_CARD = 12, R_PILL = 24, STROKE = 1;
    // ---- v0.17.76 (U1): row ----
    public static final int ROW_MIN = 56;          // one settings row is at least this tall
    public static final int HAIRLINE_ALPHA = 0x40; // divider = dim colour at this alpha (as the old section header)
    // ---- motion (ms / dp) ----
    public static final int PAGE_MS = 180;     // a page appears
    public static final int FADE_MS = 140;     // search results appear
    public static final int SLIDE_DP = 8;      // small rise while a page fades in
    public static final int MAX_MS = 400;      // cap after the system speed factor

    /** Duration after the phone's animator scale: scale <= 0 or NaN = 0 (no animation), otherwise base * scale, capped to MAX_MS. */
    public static int ms(int base, float scale) {
        if (base <= 0) return 0;
        if (Float.isNaN(scale) || scale <= 0f) return 0;
        float v = base * scale;
        if (v > MAX_MS) return MAX_MS;
        int r = Math.round(v);
        return r < 1 ? 1 : r;
    }

    /** true = animate (scale > 0). */
    public static boolean animate(float scale) { return !Float.isNaN(scale) && scale > 0f; }

    /** Slide distance in px for a density; never negative. */
    public static int slidePx(float density) {
        if (Float.isNaN(density) || density <= 0f) return 0;
        return Math.round(SLIDE_DP * density);
    }
}
