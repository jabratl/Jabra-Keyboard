package com.mgb.keyboard.typing;

/**
 * v0.17.54 (M2) the Ridmik Avro hint legends for Bangla (phonetic) letter keys (pure Java, no Android, tested by tools/AvroHintsTest.java).
 * Table (from the Ridmik Avro keyboard): q w e r t y u i o p = 1 2 3 4 5 6 7 8 9 0 (only when the number row is OFF, else no hint there),
 * a s d f g h j k l = @ # &amp; * - ! ? ( ),  z = "  b = '  n = :  m = /,  x c v and every non-letter = none.
 * English hints stay in LongPressHints; Bangla fixed (Probhat) has no hints. Shift never changes a hint.
 */
public final class AvroHints {
    private AvroHints() { }

    private static final String TOP_KEYS = "qwertyuiop";
    private static final String TOP_DIGITS = "1234567890";

    /** The hint of letter c (case ignored), or null = no hint on that key. */
    public static String hintFor(char c, boolean numRowOn) {
        char k = Character.toLowerCase(c);
        int i = TOP_KEYS.indexOf(k);
        if (i >= 0) return numRowOn ? null : String.valueOf(TOP_DIGITS.charAt(i));
        switch (k) {
            case 'a': return "@";
            case 's': return "#";
            case 'd': return "&";
            case 'f': return "*";
            case 'g': return "-";
            case 'h': return "!";
            case 'j': return "?";
            case 'k': return "(";
            case 'l': return ")";
            case 'z': return "\"";
            case 'b': return "'";
            case 'n': return ":";
            case 'm': return "/";
            default: return null;
        }
    }

    /** True = this typing state uses the Avro table: phonetic Bangla (not Probhat, not a Latin-only field) and pref bn_hints ON. */
    public static boolean active(boolean settingOn, boolean banglaMode, boolean probhat, boolean latinOnly) {
        return settingOn && banglaMode && !probhat && !latinOnly;
    }
}
