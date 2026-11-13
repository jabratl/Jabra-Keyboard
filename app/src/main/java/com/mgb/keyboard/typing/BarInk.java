package com.mgb.keyboard.typing;

/**
 * v0.17.30 (UI6): picks a readable text colour for a Clipboard panel button whose fill is the theme key colour.
 * Pure Java, no Android (tools/BarInkTest.java). WCAG relative luminance + contrast ratio.
 * pick() = the better of two candidate colours, or 0 (= "keep the old text colour") when neither reads well enough.
 */
public final class BarInk {
    private BarInk() { }

    /** Smallest contrast ratio we accept for a tinted label (WCAG large / bold text level). */
    public static final double MIN_RATIO = 3.0;

    private static double chan(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Relative luminance 0..1 of an ARGB colour (alpha ignored). */
    public static double lum(int argb) {
        return 0.2126 * chan((argb >> 16) & 0xFF) + 0.7152 * chan((argb >> 8) & 0xFF) + 0.0722 * chan(argb & 0xFF);
    }

    /** Contrast ratio 1..21 between two colours (order does not matter). */
    public static double ratio(int a, int b) {
        double la = lum(a), lb = lum(b);
        double hi = Math.max(la, lb), lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** The candidate with the best contrast on [fill]; 0 when the best one is below MIN_RATIO (caller keeps the old colour). */
    public static int pick(int fill, int candA, int candB) {
        double ra = ratio(fill, candA), rb = ratio(fill, candB);
        int best = ra >= rb ? candA : candB;
        double r = Math.max(ra, rb);
        return r >= MIN_RATIO ? best : 0;
    }
}
