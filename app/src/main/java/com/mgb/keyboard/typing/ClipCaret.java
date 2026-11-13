package com.mgb.keyboard.typing;

/**
 * v0.17.56 (C1): caret (cursor) logic for the clipboard "Edit clip" screen (pure Java, no Android).
 * Before: text could only be added / removed at the very END. Now every edit happens at a caret position
 * that the user can move (tap on the text, left / right, start / end, up / down).
 * Display text shows a visible newline mark (" \u23CE ", 3 chars) and a caret mark (CARET, 1 char).
 */
public final class ClipCaret {
    private ClipCaret() { }
    public static final String CARET = "\u258F";
    public static final String NL_MARK = " \u23CE ";

    public static int clamp(String t, int pos) {
        int n = t == null ? 0 : t.length();
        return pos < 0 ? 0 : (pos > n ? n : pos);
    }

    /** New text after inserting s at pos. */
    public static String insert(String t, int pos, String s) {
        if (t == null) t = "";
        if (s == null) s = "";
        int p = clamp(t, pos);
        return t.substring(0, p) + s + t.substring(p);
    }

    /** New position after inserting s at pos. */
    public static int afterInsert(String t, int pos, String s) {
        return clamp(t, pos) + (s == null ? 0 : s.length());
    }

    /** New text after Backspace at pos (removes the char before the caret; a surrogate pair counts as one). */
    public static String backspace(String t, int pos) {
        if (t == null) return "";
        int p = clamp(t, pos);
        if (p == 0) return t;
        return t.substring(0, p - stepBack(t, p)) + t.substring(p);
    }

    public static int afterBackspace(String t, int pos) {
        if (t == null) return 0;
        int p = clamp(t, pos);
        return p == 0 ? 0 : p - stepBack(t, p);
    }

    private static int stepBack(String t, int p) {
        if (p >= 2 && Character.isLowSurrogate(t.charAt(p - 1)) && Character.isHighSurrogate(t.charAt(p - 2))) return 2;
        return 1;
    }

    private static int stepFwd(String t, int p) {
        if (p + 1 < t.length() && Character.isHighSurrogate(t.charAt(p)) && Character.isLowSurrogate(t.charAt(p + 1))) return 2;
        return 1;
    }

    public static int left(String t, int pos) { return afterBackspace(t, pos); }

    public static int right(String t, int pos) {
        if (t == null) return 0;
        int p = clamp(t, pos);
        return p >= t.length() ? p : p + stepFwd(t, p);
    }

    /** Start of the current line (after the previous \n). */
    public static int lineStart(String t, int pos) {
        if (t == null) return 0;
        int p = clamp(t, pos);
        return p == 0 ? 0 : t.lastIndexOf('\n', p - 1) + 1;
    }

    /** End of the current line (before the next \n, or text end). */
    public static int lineEnd(String t, int pos) {
        if (t == null) return 0;
        int p = clamp(t, pos);
        int i = t.indexOf('\n', p);
        return i < 0 ? t.length() : i;
    }

    /** Up one line, keeping the column when possible. */
    public static int up(String t, int pos) {
        if (t == null) return 0;
        int p = clamp(t, pos);
        int ls = lineStart(t, p);
        if (ls == 0) return 0;
        int col = p - ls;
        int prevEnd = ls - 1;
        int prevStart = lineStart(t, prevEnd);
        return prevStart + Math.min(col, prevEnd - prevStart);
    }

    /** Down one line, keeping the column when possible. */
    public static int down(String t, int pos) {
        if (t == null) return 0;
        int p = clamp(t, pos);
        int le = lineEnd(t, p);
        if (le >= t.length()) return t.length();
        int col = p - lineStart(t, p);
        int nextStart = le + 1;
        int nextEnd = lineEnd(t, nextStart);
        return nextStart + Math.min(col, nextEnd - nextStart);
    }

    /** Text shown in the box: newline marks + the caret mark at pos. */
    public static String display(String t, int pos) {
        if (t == null) t = "";
        int p = clamp(t, pos);
        return t.substring(0, p).replace("\n", NL_MARK) + CARET + t.substring(p).replace("\n", NL_MARK);
    }

    /**
     * Tap on the display text -> caret position in the real text.
     * dispOffset = character offset inside display(t, oldPos) (from TextView layout), oldPos = caret position when it was drawn.
     * Picks the nearest gap between real characters.
     */
    public static int posFromDisplay(String t, int oldPos, int dispOffset) {
        if (t == null || t.isEmpty()) return 0;
        int op = clamp(t, oldPos);
        int caretAt = t.substring(0, op).replace("\n", NL_MARK).length();   // index of the caret mark in display
        int d = dispOffset;
        if (d > caretAt) d -= CARET.length();   // remove the caret mark from the count
        if (d < 0) d = 0;
        int acc = 0;
        for (int i = 0; i < t.length(); i++) {
            int w = t.charAt(i) == '\n' ? NL_MARK.length() : 1;
            if (d < acc + (w + 1) / 2) return i;   // nearer to the gap before char i
            acc += w;
        }
        return t.length();
    }
}
