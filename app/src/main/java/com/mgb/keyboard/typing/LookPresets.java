package com.mgb.keyboard.typing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.10.17 (D3) LOOK PRESETS: four built-in "moods" = a handful of existing prefs (theme index, look, key border, key gap, bold letters).
 * Pure Java (no Android), tested by tools/LookPresetsTest.java. Presets are CONSTANTS: nothing is stored, user profiles are never touched.
 * Applied through the normal Profiles path (ProfileStore.apply, whitelist ProfileCodec.KEYS). Theme index always 0..19 (never the custom slot).
 */
public final class LookPresets {
    private LookPresets() { }

    public static final String[] NAMES = { "AMOLED", "Reading", "Professional", "Cyber" };

    /** Keys a preset may set (all are in ProfileCodec.KEYS). */
    public static final String[] USED_KEYS = { "theme", "look", "key_border", "key_gap", "letter_bold" };

    public static int count() { return NAMES.length; }

    public static String name(int i) { return (i >= 0 && i < NAMES.length) ? NAMES[i] : ""; }

    /** Values of preset i (new map each call, fixed order). Unknown index -> empty map. */
    public static Map<String, Object> values(int i) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        switch (i) {
            case 0: set(m, 8, 1, 1, 2, false); break;    // AMOLED: Gloss Black (near-black bg), flat keys
            case 1: set(m, 5, 1, 0, 3, false); break;    // Reading: Sandstone (warm, soft), roomy gap
            case 2: set(m, 7, 1, 0, 2, false); break;    // Professional: Ivory (clean light)
            case 3: set(m, 18, 1, 1, 2, true); break;    // Cyber: Purple Haze (dark gradient), flat keys, bold letters
            default: break;
        }
        return m;
    }

    private static void set(Map<String, Object> m, int theme, int look, int border, int gap, boolean bold) {
        m.put("theme", Integer.valueOf(theme));
        m.put("look", Integer.valueOf(look));
        m.put("key_border", Integer.valueOf(border));
        m.put("key_gap", Integer.valueOf(gap));
        m.put("letter_bold", Boolean.valueOf(bold));
    }

    /** Preset as a Profile (values cleaned by ProfileCodec.clean). null for an unknown index. */
    public static ProfileCodec.Profile profile(int i) {
        if (i < 0 || i >= NAMES.length) return null;
        return new ProfileCodec.Profile(NAMES[i], ProfileCodec.clean(values(i)));
    }

    /** True when every key is whitelisted, the value has the right type and the theme is 0..19. */
    public static boolean validate(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return false;
        Map<String, Object> c = ProfileCodec.clean(m);
        if (c.size() != m.size()) return false;
        Object t = m.get("theme");
        if (!(t instanceof Integer)) return false;
        int th = ((Integer) t).intValue();
        return th >= 0 && th <= 19;
    }
}
