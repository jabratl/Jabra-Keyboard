package com.mgb.keyboard.typing;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.4.8 custom theme (theme slot 20), pure Java, tested by tools/CustomThemeTest.java.
 * Colours are ARGB ints with alpha forced to 0xFF. Saved in pref ct_json as
 * {"ver":1,"bg":"#RRGGBB","bg2":"#RRGGBB" or "","key":..,"text":..,"func":..,"round":0-100,"alpha":40-100}.
 * parse() returns null for text that is not a ver 1 theme (slot 20 is then simply not shown).
 */
public final class CustomTheme {

    public static final int VER = 1;
    public static final int ROUND_MIN = 0, ROUND_MAX = 100, ALPHA_MIN = 40, ALPHA_MAX = 100;
    public static final int BORDER_MIN = 0, BORDER_MAX = 6, SHADOW_MIN = 0, SHADOW_MAX = 4;   // v0.17.45 (P2): key outline, 0 = none, each step = 0.5 dp

    public int bg = 0xFFD6E6F5;
    public boolean hasBg2 = false;
    public int bg2 = 0xFF9C45C2;
    public int key = 0xFFFFFFFF;
    public int text = 0xFF1B3A57;
    public int func = 0xFFB5CFE8;
    public int round = 45;    // 45 -> 9 dp, the same corner as the Jabra look
    public int alpha = 100;   // key fill opacity in percent
    public int shadow = 0;    // v0.17.47 (P4): key shadow steps 0..4 (0 = none), 0.75 dp each, a darker strip under every key
    public int border = 0;    // v0.17.45 (P2): key outline steps 0..6 (0 = none = old look); colour = text colour at 50%

    public static CustomTheme defaults() { return new CustomTheme(); }

    public static final String[] PRESET_NAMES = { "AMOLED", "Reading", "Cyber", "Professional" };

    /** Ready-made colour sets (buttons in the studio that fill the fields). Unknown index = defaults. */
    public static CustomTheme preset(int i) {
        CustomTheme c = new CustomTheme();
        switch (i) {
            case 0: c.bg = 0xFF000000; c.key = 0xFF111111; c.text = 0xFFFFFFFF; c.func = 0xFF1C1C1C; break;
            case 1: c.bg = 0xFFF4ECD8; c.key = 0xFFFBF6E9; c.text = 0xFF3B2F1E; c.func = 0xFFE3D5B8; break;
            case 2: c.bg = 0xFF0A0F1F; c.hasBg2 = true; c.bg2 = 0xFF1B0B3A; c.key = 0xFF141B3D; c.text = 0xFF00FFC8; c.func = 0xFF2A1458; break;
            case 3: c.bg = 0xFFE9EDF2; c.key = 0xFFFFFFFF; c.text = 0xFF1F2937; c.func = 0xFFCBD5E1; break;
            default: break;
        }
        return c;
    }

    /** "#RRGGBB", "RRGGBB", "#RGB" or "RGB" (any case) -> opaque ARGB, or null when it is not a colour. */
    public static Integer hex(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        if (t.length() != 6) return null;
        for (int i = 0; i < 6; i++) if (Character.digit(t.charAt(i), 16) < 0) return null;
        return Integer.valueOf(0xFF000000 | Integer.parseInt(t, 16));
    }

    public static String toHex(int argb) { return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF); }

    public static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** Corner radius in dp for the Jabra look keys (round 0..100 -> 0..20 dp). */
    public float radiusDp() { return clamp(round, ROUND_MIN, ROUND_MAX) * 0.2f; }

    /** v0.17.45 (P2): outline width in dp (0 = none). */
    public float borderDp() { return clamp(border, BORDER_MIN, BORDER_MAX) * 0.5f; }

    /** v0.17.47 (P4): shadow strip height in dp (0 = none). */
    public float shadowDp() { return clamp(shadow, SHADOW_MIN, SHADOW_MAX) * 0.75f; }

    /** v0.17.47 (P4): shadow colour = black at 33%. */
    public int shadowColor() { return 0x55000000; }

    /** v0.17.45 (P2): outline colour = the text colour at 50% opacity. */
    public int borderColor() { return (text & 0xFFFFFF) | 0x80000000; }

    /** ARGB with this theme's key opacity applied to [argb]. */
    public int withKeyAlpha(int argb) {
        int a = Math.round(clamp(alpha, ALPHA_MIN, ALPHA_MAX) * 255f / 100f);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public String toJson() {
        return "{\"ver\":" + VER + ",\"bg\":\"" + toHex(bg) + "\",\"bg2\":\"" + (hasBg2 ? toHex(bg2) : "") + "\",\"key\":\"" + toHex(key)
            + "\",\"text\":\"" + toHex(text) + "\",\"func\":\"" + toHex(func) + "\",\"round\":" + clamp(round, ROUND_MIN, ROUND_MAX)
            + ",\"alpha\":" + clamp(alpha, ALPHA_MIN, ALPHA_MAX) + (clamp(border, BORDER_MIN, BORDER_MAX) > 0 ? ",\"border\":" + clamp(border, BORDER_MIN, BORDER_MAX) : "") + (clamp(shadow, SHADOW_MIN, SHADOW_MAX) > 0 ? ",\"shadow\":" + clamp(shadow, SHADOW_MIN, SHADOW_MAX) : "") + "}";   // v0.17.45: border only when set (old json unchanged)
    }

    private static final Pattern FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"([^\"]*)\"|-?\\d+)");

    /** Null = not a usable theme (not JSON-like, "ver" missing or not 1). Missing / bad single fields fall back to the defaults. */
    public static CustomTheme parse(String json) {
        try {
            if (json == null) return null;
            String s = json.trim();
            if (!s.startsWith("{") || !s.endsWith("}")) return null;
            CustomTheme c = new CustomTheme();
            boolean ver = false;
            Matcher m = FIELD.matcher(s);
            while (m.find()) {
                String k = m.group(1);
                String str = m.group(3);
                String num = str == null ? m.group(2) : null;
                if ("ver".equals(k)) { ver = num != null && Integer.parseInt(num) == VER; if (!ver) return null; }
                else if ("bg".equals(k)) { Integer v = hex(str); if (v != null) c.bg = v; }
                else if ("bg2".equals(k)) { Integer v = hex(str); if (v != null) { c.bg2 = v; c.hasBg2 = true; } else c.hasBg2 = false; }
                else if ("key".equals(k)) { Integer v = hex(str); if (v != null) c.key = v; }
                else if ("text".equals(k)) { Integer v = hex(str); if (v != null) c.text = v; }
                else if ("func".equals(k)) { Integer v = hex(str); if (v != null) c.func = v; }
                else if ("round".equals(k) && num != null) c.round = clamp(Integer.parseInt(num), ROUND_MIN, ROUND_MAX);
                else if ("alpha".equals(k) && num != null) c.alpha = clamp(Integer.parseInt(num), ALPHA_MIN, ALPHA_MAX);
                else if ("shadow".equals(k) && num != null) c.shadow = clamp(Integer.parseInt(num), SHADOW_MIN, SHADOW_MAX);   // v0.17.47 (P4)
                else if ("border".equals(k) && num != null) c.border = clamp(Integer.parseInt(num), BORDER_MIN, BORDER_MAX);   // v0.17.45 (P2)
            }
            return ver ? c : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
