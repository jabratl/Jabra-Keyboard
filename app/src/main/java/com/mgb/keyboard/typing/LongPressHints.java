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

    /** Hints work only in English typing: not in Avro / Probhat (Bangla mode), PC mode with Ctrl / Alt held, or when the setting is OFF. */
    public static boolean active(boolean settingOn, boolean banglaMode, boolean pcMetaHeld) {
        return settingOn && !banglaMode && !pcMetaHeld;
    }

    /** The small label is drawn only when hints are active AND "Hide long-press hints" is OFF. */
    public static boolean labelVisible(boolean active, boolean hide) {
        return active && !hide;
    }
}
