package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.15.7 (N7): pure rules for the custom-theme form in the app (no Android classes, tested by tools/CustomThemeFormTest.java).
 * Text fields -> CustomTheme. A theme is built ONLY when every field is valid (otherwise null: nothing is saved).
 * Field order everywhere: 0 background, 1 background 2 (empty = no gradient), 2 keys, 3 text, 4 function keys.
 */
public final class CustomThemeForm {
    private CustomThemeForm() { }

    public static final String[] NAMES = { "Background", "Background 2", "Keys", "Text", "Function keys" };

    /** True when [s] is acceptable for field [i]: a colour (CustomTheme.hex), or empty for field 1 only. */
    public static boolean fieldOk(int i, String s) {
        if (i < 0 || i > 4) return false;
        if (s == null || s.trim().isEmpty()) return i == 1;
        return CustomTheme.hex(s) != null;
    }

    /** Which of the 5 fields are valid (always length 5). */
    public static boolean[] check(String[] f) {
        boolean[] ok = new boolean[5];
        for (int i = 0; i < 5; i++) ok[i] = f != null && i < f.length && fieldOk(i, f[i]);
        return ok;
    }

    public static boolean allOk(String[] f) {
        for (boolean b : check(f)) if (!b) return false;
        return true;
    }

    /** The theme for these fields, or null when any field is invalid. round / alpha are clamped to the allowed range. */
    public static CustomTheme build(String[] f, int round, int alpha) { return build(f, round, alpha, 0); }

    /** v0.17.45 (P2): same + key outline steps (clamped 0..6). */
    public static CustomTheme build(String[] f, int round, int alpha, int border) { return build(f, round, alpha, border, 0); }

    /** v0.17.47 (P4): same + key shadow steps (clamped 0..4). */
    public static CustomTheme build(String[] f, int round, int alpha, int border, int shadow) {
        if (!allOk(f)) return null;
        CustomTheme c = new CustomTheme();
        c.bg = CustomTheme.hex(f[0]);
        String b2 = f[1] == null ? "" : f[1].trim();
        c.hasBg2 = !b2.isEmpty();
        if (c.hasBg2) c.bg2 = CustomTheme.hex(b2);
        c.key = CustomTheme.hex(f[2]);
        c.text = CustomTheme.hex(f[3]);
        c.func = CustomTheme.hex(f[4]);
        c.round = CustomTheme.clamp(round, CustomTheme.ROUND_MIN, CustomTheme.ROUND_MAX);
        c.alpha = CustomTheme.clamp(alpha, CustomTheme.ALPHA_MIN, CustomTheme.ALPHA_MAX);
        c.border = CustomTheme.clamp(border, CustomTheme.BORDER_MIN, CustomTheme.BORDER_MAX);
        c.shadow = CustomTheme.clamp(shadow, CustomTheme.SHADOW_MIN, CustomTheme.SHADOW_MAX);
        return c;
    }

    /** Field texts of a theme (hex with "#", bg2 empty when none). */
    public static String[] fields(CustomTheme c) {
        if (c == null) c = CustomTheme.defaults();
        return new String[] { CustomTheme.toHex(c.bg), c.hasBg2 ? CustomTheme.toHex(c.bg2) : "", CustomTheme.toHex(c.key), CustomTheme.toHex(c.text), CustomTheme.toHex(c.func) };
    }

    /** Readability warnings (never block saving): letters vs keys and letters vs function keys, want >= LookColors.MIN_RATIO. Empty list = fine. */
    public static List<String> warnings(CustomTheme c) {
        List<String> w = new ArrayList<String>();
        if (c == null) return w;
        if (LookColors.lowContrast(c.text, c.key)) w.add("Letters are hard to read on the keys (" + ratio(LookColors.contrast(c.text, c.key)) + ":1, want 4.5:1)");
        if (LookColors.lowContrast(c.text, c.func)) w.add("Text is hard to read on the function keys (" + ratio(LookColors.contrast(c.text, c.func)) + ":1, want 4.5:1)");
        return w;
    }

    private static String ratio(double r) { return String.format(java.util.Locale.US, "%.1f", r); }
}
