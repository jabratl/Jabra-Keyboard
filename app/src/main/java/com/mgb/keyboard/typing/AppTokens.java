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
