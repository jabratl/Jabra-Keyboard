package com.mgb.keyboard.layout;

/**
 * v0.17.6 (K5a.2) pure routing decisions of the custom keyboard (no Android): which layer the current language uses,
 * whether a key of the phonetic layer feeds the Latin buffer, the two language rules of the "?" / "." keys,
 * and when an old saved default layout must be rebuilt. The Kotlin renderer (CustomRows / MgbIme) only calls these.
 */
public final class LayoutRoute {
    private LayoutRoute() { }

    /** Stamp of the layout built by this version (v0.17.5 saved layouts have no stamp = 0). */
    public static final int BUILT_VERSION = 2;

    /** Layer of the current language. latinOnly (password / email / URL / number fields) forces English; Bangla = phonetic "av" or fixed "pb". */
    public static String layerFor(boolean bangla, boolean latinOnly, boolean probhat) {
        if (!bangla || latinOnly) return "en";
        return probhat ? "pb" : "av";
    }

    /** True when [out] is exactly one Latin letter: in layer "av" such a key feeds the phonetic buffer (like onLetter), anything else is typed as text. */
    public static boolean avBuffer(String out) {
        if (out == null || out.length() != 1) return false;
        char c = out.charAt(0);
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * The old language rule of the "?" key (id b_qm: Bangla types the danda) and of the long-press of "." (id b_period, text "?").
     * [banglaActive] = Bangla is on AND the field is not latinOnly. Any other key / text = unchanged.
     */
    public static String mapQm(String keyId, String text, boolean banglaActive) {
        if (text == null) return "";
        if (!banglaActive || keyId == null) return text;
        if ("b_qm".equals(keyId) && "?".equals(text)) return "\u0964";
        if ("b_period".equals(keyId) && "?".equals(text)) return "\u0964";
        return text;
    }

    /** Label of the "?" key follows the language the same way (b_qm only). */
    public static String qmLabel(String keyId, String label, boolean bangla) {
        if ("b_qm".equals(keyId) && "?".equals(label) && bangla) return "\u0964";
        return label == null ? "" : label;
    }

    // ---------- v0.17.18 (L1): "danda dot" = the dot key follows the Ridmik rule in Bangla ----------
    /** Default of pref danda_key (ON: Bangla dot key = danda, long-press = full stop). One place, so every reader agrees. */
    public static final boolean DANDA_DEF = true;
    private static final String DANDA = "\u0964";

    /** The rule is on only when the pref is on AND the separate "?" / danda key is hidden (else the danda would be there twice). */
    public static boolean dandaDot(boolean pref, boolean qmShown) { return pref && !qmShown; }

    /** Text typed by a TAP on the classic dot key. [banglaActive] = Bangla on and the field is not latinOnly. */
    public static String dotTap(boolean dandaDot, boolean banglaActive) { return dandaDot && banglaActive ? DANDA : "."; }

    /** Text typed by a LONG-PRESS on the classic dot key: rule ON + Bangla = full stop; old rule = danda in Bangla, "?" otherwise. */
    public static String dotLong(boolean dandaDot, boolean banglaActive) {
        if (banglaActive) return dandaDot ? "." : DANDA;
        return "?";
    }

    /** Label of the dot key for the classic row. */
    public static String dotLabel(boolean dandaDot, boolean banglaActive) { return dotTap(dandaDot, banglaActive); }

    /**
     * Custom layout: like {@link #mapQm}, plus the danda-dot rule for key b_period. Only the factory texts are mapped
     * ("." -> danda, long-press "?" -> "."); a text the user typed into the editor (anything else) is never touched.
     */
    public static String mapDot(String keyId, String text, boolean banglaActive, boolean dandaDot) {
        if (text == null) return "";
        if (dandaDot && banglaActive && "b_period".equals(keyId)) {
            if (".".equals(text)) return DANDA;
            if ("?".equals(text)) return ".";
        }
        return mapQm(keyId, text, banglaActive);
    }

    /** Custom layout label: danda-dot rule for b_period, else the old {@link #qmLabel}. */
    public static String dotLabel(String keyId, String label, boolean bangla, boolean banglaActive, boolean dandaDot) {
        if (dandaDot && banglaActive && "b_period".equals(keyId) && ".".equals(label)) return DANDA;
        return qmLabel(keyId, label, bangla);
    }

    /** Number row: a single Latin digit may be shown / typed as a Bangla digit (pref numrow_bn); anything else stays. [shown] = the text the classic number row would use. */
    public static boolean isDigit(String out) {
        return out != null && out.length() == 1 && out.charAt(0) >= '0' && out.charAt(0) <= '9';
    }

    /** A saved layout built before v0.17.6 and never edited is rebuilt once from the current settings (the editor does not exist yet, nothing can be lost). */
    public static boolean needsRebuild(int builtVersion, boolean edited) {
        return !edited && builtVersion < BUILT_VERSION;
    }

    /** Index where the mode key (PH / PC, only when the edit bar is OFF) goes in the custom bottom row: after the leading sym and lang keys. */
    public static int modeKeyIndex(RowDef bottom) {
        if (bottom == null) return 0;
        int i = 0;
        for (KeyDef k : bottom.keys) {
            if (k.kind == KeyDef.ACTION && (k.action.equals("sym") || k.action.equals("lang"))) i++;
            else break;
        }
        return i;
    }
}
