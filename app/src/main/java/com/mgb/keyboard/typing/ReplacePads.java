package com.mgb.keyboard.typing;

/**
 * v0.24.0 (R1): key pages of the small keyboard inside the Replace panel (pure Java data, tested by tools/ReplacePadsTest.java).
 * Needed because an IME panel cannot hold a normal text box. Pages: abc | symbols + regex helpers | Bangla consonants | Bangla vowels, signs, digits.
 * Every row = tokens separated by one space. A token is the key label AND the text it types, except:
 *   "ZWNJ" types U+200C, "ZWJ" types U+200D, a token that starts with U+25CC (dotted circle) types the part after it (vowel signs need a base to look right).
 */
public final class ReplacePads {

    private ReplacePads() { }

    public static final String[] PAGE_NAMES = { "abc", "#+=", "\u09AC\u09BE\u0981", "\u09AC\u09BE\u0982" };   // abc | #+= | বাঁ | বাং (short names of the page key)

    public static final String[][] ROWS = {
        { "1 2 3 4 5 6 7 8 9 0", "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m" },
        { "\\ $ ^ . * + ? | ( )", "[ ] { } < > - _ = /", "@ # % & ! : ; ' \" ,", "\\n \\t \\d \\s \\b .* $1 \\U \\E" },
        { "\u0995 \u0996 \u0997 \u0998 \u0999 \u099A \u099B \u099C \u099D \u099E",
          "\u099F \u09A0 \u09A1 \u09A2 \u09A3 \u09A4 \u09A5 \u09A6 \u09A7 \u09A8",
          "\u09AA \u09AB \u09AC \u09AD \u09AE \u09AF \u09B0 \u09B2 \u09B6 \u09B7",
          "\u09B8 \u09B9 \u09DC \u09DD \u09DF \u09CE \u25CC\u0982 \u25CC\u0983 \u25CC\u0981" },
        { "\u0985 \u0986 \u0987 \u0988 \u0989 \u098A \u098B \u098F \u0990 \u0993 \u0994",
          "\u25CC\u09BE \u25CC\u09BF \u25CC\u09C0 \u25CC\u09C1 \u25CC\u09C2 \u25CC\u09C3 \u25CC\u09C7 \u25CC\u09C8 \u25CC\u09CB \u25CC\u09CC",
          "\u25CC\u09CD \u0964 \u09E6 \u09E7 \u09E8 \u09E9 \u09EA \u09EB",
          "\u09EC \u09ED \u09EE \u09EF \u09F3 ZWNJ ZWJ" }
    };

    public static final int MAX_PER_ROW = 11;
    public static final int FIELD_MAX = 500;   // characters of Find / With

    /** Text on the key. */
    public static String label(String tok) {
        return tok == null ? "" : tok;
    }

    /** Text the key types. */
    public static String value(String tok) {
        if (tok == null) return "";
        if (tok.equals("ZWNJ")) return "\u200C";
        if (tok.equals("ZWJ")) return "\u200D";
        if (tok.length() > 1 && tok.charAt(0) == '\u25CC') return tok.substring(1);
        return tok;
    }

    public static String[] tokens(int page, int row) {
        if (page < 0 || page >= ROWS.length || row < 0 || row >= ROWS[page].length) return new String[0];
        return ROWS[page][row].split(" ");
    }

    public static int next(int page) {
        return (page + 1) % ROWS.length;
    }

    /** Adds typed text to a field, never beyond FIELD_MAX. */
    public static String append(String cur, String add) {
        String c = cur == null ? "" : cur;
        if (add == null || add.length() == 0) return c;
        if (c.length() + add.length() > FIELD_MAX) return c;
        return c + add;
    }

    /** Removes the last character (a whole surrogate pair counts as one). */
    public static String backspace(String cur) {
        if (cur == null || cur.length() == 0) return "";
        int n = cur.length();
        int k = Character.isLowSurrogate(cur.charAt(n - 1)) && n >= 2 && Character.isHighSurrogate(cur.charAt(n - 2)) ? 2 : 1;
        return cur.substring(0, n - k);
    }
}
