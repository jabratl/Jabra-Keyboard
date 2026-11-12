package com.mgb.keyboard.typing;

/** v0.1.5 Jabra look colours (pure Java, no Android). ARGB ints. */
public final class LookColors {
    private LookColors() { }

    /** Function-key fill per theme index (0 Classic, 1 Blue, 2 Mint, 3 Sand, 4 Dark; v0.2.4: 5..17, v0.2.6: 18..19). Unknown index -> Classic value. */
    public static int funcFill(int themeIndex) {
        switch (themeIndex) {
            case 1: return 0xFFB5CFE8;
            case 2: return 0xFFB4DCC8;
            case 3: return 0xFFE0D2B8;
            case 4: return 0xFF2B2D31;
            case 5: return 0xFFD2C6AB;   // v0.2.4 new themes 5..17 (same order as JabraThemes.ALL)
            case 6: return 0xFF24302B;
            case 7: return 0xFFE2DDD0;
            case 8: return 0xFF16161A;
            case 9: return 0xFF242424;
            case 10: return 0xFF16213A;
            case 11: return 0xFFAEBFE0;
            case 12: return 0xFF1556A0;
            case 13: return 0xFF14352C;
            case 14: return 0xFF9FDCCD;
            case 15: return 0xFFE9B3C5;
            case 16: return 0xFFA0A5AE;
            case 17: return 0xFF332B52;
            case 18: return 0xFF4A2C80;   // v0.2.6 gradient themes 18..19
            case 19: return 0xFFFFC9BA;
            default: return 0xFFC3C9D0;
        }
    }

    /** Darker colour for the pressed state: each RGB channel * (1 - f), alpha kept. f is clamped to 0..1. */
    public static int darken(int argb, float f) {
        float k = 1f - (f < 0f ? 0f : (f > 1f ? 1f : f));
        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >> 16) & 0xFF) * k);
        int g = Math.round(((argb >> 8) & 0xFF) * k);
        int b = Math.round((argb & 0xFF) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---- v0.10.17 (D3): contrast helpers (WCAG 2.x relative luminance / contrast ratio). Pure, no Android.

    /** Smallest contrast ratio we accept for text (WCAG AA, normal text). */
    public static final double MIN_RATIO = 4.5;

    private static double lin(int c8) {
        double s = c8 / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** Relative luminance 0..1 (alpha ignored). */
    public static double luminance(int argb) {
        return 0.2126 * lin((argb >> 16) & 0xFF) + 0.7152 * lin((argb >> 8) & 0xFF) + 0.0722 * lin(argb & 0xFF);
    }

    /** Contrast ratio 1..21 between two colours (alpha ignored, order does not matter). */
    public static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        double hi = x > y ? x : y, lo = x > y ? y : x;
        return (hi + 0.05) / (lo + 0.05);
    }

    /** True when text on fill is hard to read (ratio < MIN_RATIO). */
    public static boolean lowContrast(int text, int fill) { return contrast(text, fill) < MIN_RATIO; }

    /** Worst contrast of c against the bg and (when hasBg2) the second gradient colour. */
    public static double minOnBg(int c, int bg, boolean hasBg2, int bg2) {
        double r = contrast(c, bg);
        if (hasBg2) { double r2 = contrast(c, bg2); if (r2 < r) r = r2; }
        return r;
    }

    /**
     * Small help-text colour. Old rule (dark bg -> #B8B8B8, else #555555) is kept whenever it reads well (>= 4.5:1 on the bg and on bg2),
     * so Classic and every theme that was already fine look EXACTLY as before. Otherwise: the theme text colour when that reads
     * (hasText), else white or black, whichever contrasts more.
     */
    public static int noteColor(int bg, boolean hasBg2, int bg2, boolean hasText, int text) {
        int old = luminance(bg) < 0.4 ? 0xFFB8B8B8 : 0xFF555555;
        if (minOnBg(old, bg, hasBg2, bg2) >= MIN_RATIO) return old;
        if (hasText && minOnBg(text, bg, hasBg2, bg2) >= MIN_RATIO) return text | 0xFF000000;
        double w = minOnBg(0xFFFFFFFF, bg, hasBg2, bg2), k = minOnBg(0xFF000000, bg, hasBg2, bg2);
        return w >= k ? 0xFFFFFFFF : 0xFF000000;
    }
}
