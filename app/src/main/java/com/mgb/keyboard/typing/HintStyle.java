package com.mgb.keyboard.typing;

/**
 * v0.8.6 hint label look (pure Java, no Android, tested by tools/HintStyleTest.java).
 * - Bangla digit hint for the number row: key "1" shows and types the small hint \u09E7 (pref hint_digits, default OFF).
 * - Hint size level (pref hint_size_lv) and hint opacity level (pref hint_alpha_lv). Level 1 = "Normal" = EXACTLY the old look
 *   (text height 0.27 x key height, alpha 170), so nothing changes until the user picks another level.
 */
public final class HintStyle {
    private HintStyle() { }

    public static final String BN_DIGITS = "\u09E7\u09E8\u09E9\u09EA\u09EB\u09EC\u09ED\u09EE\u09EF\u09E6";   // 1 2 3 4 5 6 7 8 9 0
    public static final String LATIN_DIGITS = "1234567890";

    public static final String[] SIZE_NAMES = { "Small", "Normal", "Large", "Extra" };
    public static final String[] ALPHA_NAMES = { "Faint", "Normal", "Strong", "Full" };
    public static final int LV_DEFAULT = 1;
    private static final float[] SIZE_F = { 0.80f, 1.00f, 1.20f, 1.40f };
    private static final int[] ALPHA_I = { 110, 170, 215, 255 };

    public static int clampLv(int lv) { return lv < 0 ? 0 : (lv > 3 ? 3 : lv); }

    public static float sizeFactor(int lv) { return SIZE_F[clampLv(lv)]; }

    /** Paint alpha 0..255. */
    public static int alpha(int lv) { return ALPHA_I[clampLv(lv)]; }

    /** Hint text size in px for a key [keyHeightPx] high: old rule (0.27 x height, 9..40) with the size factor on top, then the same 9..40 limit. */
    public static float textSize(float keyHeightPx, int lv) {
        float s = keyHeightPx * 0.27f * sizeFactor(lv);
        return s < 9f ? 9f : (s > 40f ? 40f : s);
    }

    /**
     * v0.9.4 (R4): main label AND typed text of a number-row key. [bnOn] = pref numrow_bn, [banglaActive] = a Bangla layout is active AND the field
     * is not Latin-only (password / email / URL / number fields). Anything else = the Latin digit, exactly as before.
     */
    public static String digitLabel(char c, boolean bnOn, boolean banglaActive) {
        if (!bnOn || !banglaActive || !isDigit(c)) return String.valueOf(c);
        return String.valueOf(BN_DIGITS.charAt(LATIN_DIGITS.indexOf(c)));
    }

    /**
     * v0.9.4 (R4): the small long-press hint of a number-row key. Bangla number row active = the LATIN digit (the main label is Bangla now);
     * otherwise = the old v0.8.6 rule (Bangla digit hint when hint_digits is ON, else none). null = no hint.
     */
    public static String digitHintFor(char c, boolean hintDigits, boolean bnOn, boolean banglaActive) {
        if (!isDigit(c)) return null;
        if (bnOn && banglaActive) return String.valueOf(c);
        return digitHint(c, hintDigits);
    }

    public static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    /** The Bangla digit for a Latin digit key, or null when [on] is false or [c] is not 0-9. */
    public static String digitHint(char c, boolean on) {
        if (!on || !isDigit(c)) return null;
        int i = LATIN_DIGITS.indexOf(c);
        return i < 0 ? null : String.valueOf(BN_DIGITS.charAt(i));
    }

    // ---- v0.17.82 (U6): Smart key hints (pref hint_mode: 0 Smart = default, 1 Always, 2 Off). hint_on / hint_hide keep their old meaning. ----
    public static final String[] MODE_NAMES = { "Smart", "Always", "Off" };
    public static final int MODE_SMART = 0, MODE_ALWAYS = 1, MODE_OFF = 2;
    /** Smart: no hint label on a key lower than this (dp): it would be too small to read. Default key height (48 dp, min 70 %) never goes below it. */
    public static final float MIN_KEY_DP = 30f;
    /** Smart: a hint must reach this contrast against the key colour (WCAG large-text level) or its colour is replaced. */
    public static final double MIN_CONTRAST = 3.0;

    public static int clampMode(int m) { return (m < 0 || m > 2) ? MODE_SMART : m; }

    /** Long-press typing: Off stops it, otherwise the old hint_on switch decides. */
    public static boolean typingOn(int mode, boolean hintOn) { return clampMode(mode) != MODE_OFF && hintOn; }

    /** Small label: typing on AND the old "Hide long-press hints" switch OFF. */
    public static boolean labelOn(int mode, boolean hintOn, boolean hide) { return typingOn(mode, hintOn) && !hide; }

    /** Smart only: the label is skipped on a key that is too low to read. Always / Off: never (Off draws nothing anyway). */
    public static boolean tooSmall(int mode, float keyHeightDp) { return clampMode(mode) == MODE_SMART && keyHeightDp > 0f && keyHeightDp < MIN_KEY_DP; }

    private static double lin(int v) { double c = v / 255.0; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
    private static double lum(int c) { return 0.2126 * lin((c >> 16) & 255) + 0.7152 * lin((c >> 8) & 255) + 0.0722 * lin(c & 255); }

    /** WCAG contrast ratio 1..21 of two opaque colours (alpha bits ignored). */
    public static double contrast(int a, int b) {
        double x = lum(a), y = lum(b);
        double hi = Math.max(x, y), lo = Math.min(x, y);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** [fg] drawn with [alpha] (0..255) over the opaque [bg] = the colour the eye sees. */
    public static int blend(int fg, int bg, int alpha) {
        int a = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        int r = (((fg >> 16) & 255) * a + ((bg >> 16) & 255) * (255 - a)) / 255;
        int g = (((fg >> 8) & 255) * a + ((bg >> 8) & 255) * (255 - a)) / 255;
        int b = ((fg & 255) * a + (bg & 255) * (255 - a)) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Smart colour: keeps the theme colour + the chosen opacity when that already reads (>= MIN_CONTRAST on [keyFill]), so every theme that
     * worked before looks exactly the same. Otherwise black or white (the better one); if even that is too faint at the chosen opacity, opacity 255.
     * Returns { colour (opaque), alpha }.
     */
    public static int[] readable(int color, int keyFill, int alpha) {
        int a = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        int col = 0xFF000000 | (color & 0xFFFFFF), fill = 0xFF000000 | (keyFill & 0xFFFFFF);
        if (contrast(blend(col, fill, a), fill) >= MIN_CONTRAST) return new int[] { col, a };
        int black = 0xFF000000, white = 0xFFFFFFFF;
        int best = contrast(blend(black, fill, a), fill) >= contrast(blend(white, fill, a), fill) ? black : white;
        if (contrast(blend(best, fill, a), fill) >= MIN_CONTRAST) return new int[] { best, a };
        return new int[] { best, 255 };
    }
}
