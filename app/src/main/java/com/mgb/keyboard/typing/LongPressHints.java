package com.mgb.keyboard.typing;

/**
 * v0.1.6 long-press hint letters (pure Java, no Android).
 * hintFor(letter, numRowOn) = the small character shown on a letter key and typed on long-press.
 * Number row ON: q..p get symbols (digits would be duplicates). Number row OFF: q..p get digits 1..0.
 * Returns null when the key has no hint (not a-z). Hints are never changed by shift.
 */
public final class LongPressHints {
    private LongPressHints() { }

    private static final String TOP_DIGITS = "1234567890";   // q w e r t y u i o p
    private static final String TOP_SYMBOLS = "~`^={}[]<>";   // q w e r t y u i o p (number row ON)
    private static final String TOP_KEYS = "qwertyuiop";

    public static String hintFor(char c, boolean numRowOn) {
        char k = Character.toLowerCase(c);
        int i = TOP_KEYS.indexOf(k);
        if (i >= 0) return String.valueOf((numRowOn ? TOP_SYMBOLS : TOP_DIGITS).charAt(i));
        switch (k) {
            case 'a': return "@";
            case 's': return "#";
            case 'd': return "$";
            case 'f': return "%";
            case 'g': return "&";
            case 'h': return "-";
            case 'j': return "+";
            case 'k': return "(";
            case 'l': return ")";
            case 'z': return "*";
            case 'x': return "\"";
            case 'c': return "'";
            case 'v': return ":";
            case 'b': return ";";
            case 'n': return "!";
            case 'm': return "?";
            default: return null;
        }
    }

    /** v0.5.2 custom override text, e.g. "a=@,s=#": letter a-z = ONE character. Max 26 entries. Empty = nothing changed (old table). */
    public static final int CUSTOM_MAX = 26;

    private static boolean okValue(String v) {
        if (v == null || v.isEmpty() || v.length() > 2) return false;
        int cp = v.codePointAt(0);
        if (Character.charCount(cp) != v.length()) return false;
        return !Character.isWhitespace(cp) && !Character.isISOControl(cp) && cp != ',' && cp != '=';
    }

    /** Clean text: bad / duplicate / over-limit entries dropped, letters lower-cased, order kept. "" when nothing is valid. */
    public static String normalizeCustom(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        String seen = "";
        int n = 0;
        for (String part : text.split(",")) {
            int e = part.indexOf('=');
            if (e != 1) continue;
            char k = Character.toLowerCase(part.charAt(0));
            String v = part.substring(2);
            if (k < 'a' || k > 'z' || !okValue(v) || seen.indexOf(k) >= 0 || n >= CUSTOM_MAX) continue;
            seen += k;
            if (out.length() > 0) out.append(',');
            out.append(k).append('=').append(v);
            n++;
        }
        return out.toString();
    }

    /** The custom character for letter c, or null (no entry / empty text). */
    public static String customFor(char c, String custom) {
        if (custom == null || custom.isEmpty()) return null;
        char k = Character.toLowerCase(c);
        for (String part : normalizeCustom(custom).split(",")) {
            if (part.length() >= 3 && part.charAt(0) == k) return part.substring(2);
        }
        return null;
    }

    /** Custom entry first, else the normal table (hintFor). */
    public static String hintFor(char c, boolean numRowOn, String custom) {
        String x = customFor(c, custom);
        return x != null ? x : hintFor(c, numRowOn);
    }

    /** Hints work only in English typing: not in Avro / Probhat (Bangla mode), PC mode with Ctrl / Alt held, or when the setting is OFF. */
    public static boolean active(boolean settingOn, boolean banglaMode, boolean pcMetaHeld) {
        return settingOn && !banglaMode && !pcMetaHeld;
    }

    /** The small label is drawn only when hints are active AND "Hide long-press hints" is OFF. */
    public static boolean labelVisible(boolean active, boolean hide) {
        return active && !hide;
    }

    /**
     * v0.17.82 (U6): is a hint worth showing on this key? Always = any non-empty hint. Smart / Off = not empty and not the same text as the key's own label
     * (a hint that repeats the key says nothing).
     */
    public static boolean useful(int mode, String hint, String keyLabel) {
        if (hint == null || hint.isEmpty()) return false;
        if (HintStyle.clampMode(mode) == HintStyle.MODE_ALWAYS) return true;
        return keyLabel == null || !hint.equalsIgnoreCase(keyLabel.trim());
    }
}
