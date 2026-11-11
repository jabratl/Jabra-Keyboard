package com.mgb.keyboard.typing;

/**
 * v0.10.9 (D1) colours of the APP SCREEN (not the keyboard themes). Pure Java, ARGB ints.
 * Mode (pref app_theme): 0 = Auto (follow the phone), 1 = Light (default = today's look), 2 = Dark. Anything else = Light.
 * LIGHT: the app keeps the system light theme; only the colours the app sets itself come from here
 *   (help text dim #6B6B6B instead of #888888 = the ONE visible light-mode difference, contrast 3.5 -> 5.3; warning orange #D84315 unchanged).
 * DARK: near-black #121316, cards #1E1F22 (same family as keyboard theme 4), text #F2F2F2, dim #B8B8B8, accent #2271B1 (caps-lock blue), warning #FF8A65.
 */
public final class AppPalette {
    private AppPalette() { }

    public static final int AUTO = 0, LIGHT = 1, DARK = 2;
    /** WCAG AA for normal text. */
    public static final double MIN_CONTRAST = 4.5;

    public static int normalize(int mode) { return (mode == AUTO || mode == LIGHT || mode == DARK) ? mode : LIGHT; }

    /** Is the dark palette used? Auto follows phoneIsDark; a bad mode = light. */
    public static boolean pick(int mode, boolean phoneIsDark) {
        int m = normalize(mode);
        return m == DARK || (m == AUTO && phoneIsDark);
    }

    /** Next mode when the user taps the button: Light > Dark > Auto > Light. A bad value starts at Dark. */
    public static int next(int mode) {
        switch (normalize(mode)) { case LIGHT: return DARK; case DARK: return AUTO; default: return LIGHT; }
    }

    public static String name(int mode) {
        switch (normalize(mode)) { case DARK: return "Dark"; case AUTO: return "Auto (follow phone)"; default: return "Light"; }
    }

    public static int bg(boolean dark)      { return dark ? 0xFF121316 : 0xFFFFFFFF; }
    public static int card(boolean dark)    { return dark ? 0xFF1E1F22 : 0xFFF5F5F5; }
    public static int text(boolean dark)    { return dark ? 0xFFF2F2F2 : 0xFF212121; }
    public static int dim(boolean dark)     { return dark ? 0xFFB8B8B8 : 0xFF6B6B6B; }
    public static int warn(boolean dark)    { return dark ? 0xFFFF8A65 : 0xFFD84315; }
    public static int ok(boolean dark)      { return dark ? 0xFF81C784 : 0xFF2E7D32; }
    public static int accent()              { return 0xFF2271B1; }
    public static int onAccent()            { return 0xFFFFFFFF; }

    private static double lin(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    public static double luminance(int argb) {
        return 0.2126 * lin((argb >> 16) & 0xff) + 0.7152 * lin((argb >> 8) & 0xff) + 0.0722 * lin(argb & 0xff);
    }

    /** WCAG contrast ratio 1..21. */
    public static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        double hi = Math.max(x, y), lo = Math.min(x, y);
        return (hi + 0.05) / (lo + 0.05);
    }
}
