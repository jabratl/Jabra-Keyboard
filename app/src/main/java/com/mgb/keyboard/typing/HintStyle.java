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
}
