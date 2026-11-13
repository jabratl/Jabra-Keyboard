package com.mgb.keyboard.typing;

/**
 * v0.17.58 (C3): WCAG contrast helper for the clipboard look rules (text >= 4.5:1, icons / borders >= 3:1).
 * Pure Java (tools/ClipContrastTest.java). Colours are 0xAARRGGBB ints; alpha is ignored (opaque). Never throws.
 */
public final class ClipContrast {
    private ClipContrast() { }

    public static final double TEXT_MIN = 4.5, ICON_MIN = 3.0;

    private static double lin(int c8) {
        double c = c8 / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double luminance(int argb) {
        return 0.2126 * lin((argb >> 16) & 0xFF) + 0.7152 * lin((argb >> 8) & 0xFF) + 0.0722 * lin(argb & 0xFF);
    }

    /** Contrast ratio 1.0 .. 21.0 (same for either order). */
    public static double ratio(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        double hi = Math.max(la, lb), lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    public static boolean textOk(int ink, int bg) { return ratio(ink, bg) >= TEXT_MIN; }

    public static boolean iconOk(int ink, int bg) { return ratio(ink, bg) >= ICON_MIN; }
}
