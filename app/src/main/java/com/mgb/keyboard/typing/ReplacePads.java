package com.mgb.keyboard.typing;

/**
 * v0.24.0 (R1): key pages of the small keyboard inside the Replace panel (pure Java data, tested by tools/ReplacePadsTest.java).
 * Needed because an IME panel cannot hold a normal text box. Pages: abc | symbols + regex helpers (v0.42.0: the Bangla pages were removed).
 * Every row = tokens separated by one space. A token is the key label AND the text it types, except:
 *   "ZWNJ" types U+200C, "ZWJ" types U+200D, a token that starts with U+25CC (dotted circle) types the part after it (vowel signs need a base to look right).
 */
public final class ReplacePads {

    private ReplacePads() { }

    public static final String[] PAGE_NAMES = { "abc", "#+=" };   // v0.42.0: only 2 pages (the 2 Bangla pages were removed)

    public static final String[][] ROWS = {
        { "1 2 3 4 5 6 7 8 9 0", "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m" },
        { "\\ $ ^ . * + ? | ( )", "[ ] { } < > - _ = /", "@ # % & ! : ; ' \" ,", "\\n \\t \\d \\s \\b .* $1 \\U \\E" }
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

    // ---- v0.29.0: caret inside Find / With (surrogate-safe, never throws)

    public static int clampCaret(String cur, int caret) {
        int n = cur == null ? 0 : cur.length();
        int c = caret < 0 ? 0 : Math.min(caret, n);
        if (c > 0 && c < n && Character.isLowSurrogate(cur.charAt(c)) && Character.isHighSurrogate(cur.charAt(c - 1))) c--;
        return c;
    }

    public static int left(String cur, int caret) {
        int c = clampCaret(cur, caret);
        if (c <= 0) return 0;
        return clampCaret(cur, c - 1);
    }

    public static int right(String cur, int caret) {
        int n = cur == null ? 0 : cur.length();
        int c = clampCaret(cur, caret);
        if (c >= n) return n;
        int k = Character.isHighSurrogate(cur.charAt(c)) && c + 1 < n && Character.isLowSurrogate(cur.charAt(c + 1)) ? 2 : 1;
        return c + k;
    }

    /** Inserts add at the caret, never beyond FIELD_MAX (then cur is returned unchanged). */
    public static String insertAt(String cur, int caret, String add) {
        String c0 = cur == null ? "" : cur;
        if (add == null || add.length() == 0) return c0;
        if (c0.length() + add.length() > FIELD_MAX) return c0;
        int c = clampCaret(c0, caret);
        return c0.substring(0, c) + add + c0.substring(c);
    }

    /** Removes the character before the caret (a whole surrogate pair counts as one). */
    public static String backspaceAt(String cur, int caret) {
        String c0 = cur == null ? "" : cur;
        int c = clampCaret(c0, caret);
        if (c <= 0) return c0;
        int from = left(c0, c);
        return c0.substring(0, from) + c0.substring(c);
    }

    /** Text for the one-line display: caret mark (U+258F) at the caret; long text = a window of about max characters around the caret with ellipsis marks. */
    public static String view(String cur, int caret, int max) {
        String c0 = cur == null ? "" : cur;
        int c = clampCaret(c0, caret);
        int from = 0, to = c0.length();
        if (c0.length() > max) {
            from = Math.max(0, c - max / 2);
            to = Math.min(c0.length(), from + max);
            from = Math.max(0, to - max);
            if (from > 0 && from < c0.length() && Character.isLowSurrogate(c0.charAt(from))) from++;
            if (to < c0.length() && to > 0 && Character.isHighSurrogate(c0.charAt(to - 1))) to--;
            if (c < from) c = from;
            if (c > to) c = to;
        }
        return (from > 0 ? "\u2026" : "") + c0.substring(from, c) + "\u258F" + c0.substring(c, to) + (to < c0.length() ? "\u2026" : "");
    }
}
