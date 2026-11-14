package com.mgb.keyboard.typing;

/**
 * v0.10.9 (D1) pure colour table for the APP screen (Settings app only; keyboard themes are NOT touched).
 * Modes: AUTO (follow phone) / LIGHT / DARK. Any unknown mode = LIGHT (today's look).
 * No Android classes here so tools/AppPaletteTest.java can run it on a computer.
 */
public final class AppPalette {
    private AppPalette() { }

    public static final int AUTO = 0, LIGHT = 1, DARK = 2;

    // LIGHT
    private static final int L_BG = 0xFFFFFFFF, L_CARD = 0xFFF2F2F2, L_TEXT = 0xFF212121, L_DIM = 0xFF6B6B6B,
            L_WARN = 0xFFBF360C, L_OK = 0xFF2E7D32, L_ACCENT = 0xFF1565C0;
    // DARK
    // v0.17.76 (U1): outline (hairlines / strokes), surface2 (pressed / unselected segment, deeper than card), accentSoft (tinted selection / flash), onAccent (text on an accent fill).
    // Rule: dim text is only for bg / card (4.5:1); on surface2 use the normal text colour.
    private static final int L_OUTLINE = 0xFFD6D6D6, L_SURFACE2 = 0xFFE6E6E6, L_ACCENT_SOFT = 0xFFDCE9FA, L_ON_ACCENT = 0xFFFFFFFF;
    private static final int D_OUTLINE = 0xFF3A3C41, D_SURFACE2 = 0xFF2A2C30, D_ACCENT_SOFT = 0xFF243447, D_ON_ACCENT = 0xFF121316;
    private static final int D_BG = 0xFF121316, D_CARD = 0xFF1E1F22, D_TEXT = 0xFFF2F2F2, D_DIM = 0xFFB8B8B8,
            D_WARN = 0xFFFF8A65, D_OK = 0xFF81C784, D_ACCENT = 0xFF90CAF9;

    /** Unknown / out-of-range value -> LIGHT. */
    public static int sanitize(int mode) { return (mode == AUTO || mode == LIGHT || mode == DARK) ? mode : LIGHT; }

    /** AUTO follows the phone flag; LIGHT / DARK ignore it. */
    public static boolean isDark(int mode, boolean phoneIsDark) {
        int m = sanitize(mode);
        return m == DARK || (m == AUTO && phoneIsDark);
    }

    /** Tap order: Light -> Dark -> Auto -> Light. */
    public static int next(int mode) {
        switch (sanitize(mode)) {
            case LIGHT: return DARK;
            case DARK: return AUTO;
            default: return LIGHT;
        }
    }

    public static String name(int mode) {
        switch (sanitize(mode)) {
            case DARK: return "Dark";
            case AUTO: return "Auto (follow phone)";
            default: return "Light";
        }
    }

    public static int bg(boolean dark) { return dark ? D_BG : L_BG; }
    public static int card(boolean dark) { return dark ? D_CARD : L_CARD; }
    public static int text(boolean dark) { return dark ? D_TEXT : L_TEXT; }
    public static int dim(boolean dark) { return dark ? D_DIM : L_DIM; }
    public static int warn(boolean dark) { return dark ? D_WARN : L_WARN; }
    public static int ok(boolean dark) { return dark ? D_OK : L_OK; }
    public static int accent(boolean dark) { return dark ? D_ACCENT : L_ACCENT; }
    public static int outline(boolean dark) { return dark ? D_OUTLINE : L_OUTLINE; }          // v0.17.76 (U1)
    public static int surface2(boolean dark) { return dark ? D_SURFACE2 : L_SURFACE2; }
    public static int accentSoft(boolean dark) { return dark ? D_ACCENT_SOFT : L_ACCENT_SOFT; }
    public static int onAccent(boolean dark) { return dark ? D_ON_ACCENT : L_ON_ACCENT; }

    private static double lin(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double lum(int argb) {
        return 0.2126 * lin((argb >> 16) & 0xFF) + 0.7152 * lin((argb >> 8) & 0xFF) + 0.0722 * lin(argb & 0xFF);
    }

    /** WCAG contrast ratio (1.0 .. 21.0). */
    public static double contrast(int a, int b) {
        double la = lum(a), lb = lum(b);
        double hi = Math.max(la, lb), lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }
}
