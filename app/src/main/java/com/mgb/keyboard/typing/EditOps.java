package com.mgb.keyboard.typing;

/**
 * v0.16.13 (X13 part 1) pure edit helpers for the new toolbar items (no Android classes, tested on a computer).
 *
 * MODEL: the caller builds ONE string = text before the selection + selected text + text after the selection
 * (windows read with getTextBeforeCursor / getSelectedText / getTextAfterCursor) and passes it with
 * selStart / selEnd measured INSIDE that string. Every result is a range inside the same string; the caller adds
 * its own base offset (absolute selection start - length of the "before" window) before calling setSelection /
 * deleteSurroundingText. Nothing here touches the field.
 *
 * Rules shared by all methods:
 *  - null text = "". selStart / selEnd are clamped to 0..length and swapped when selStart > selEnd.
 *  - A result never cuts a surrogate pair (emoji) in half.
 *  - A WORD = letters, digits, "_", combining marks (Bangla kar, hasanta, chandrabindu, etc.) and ZWJ / ZWNJ.
 *    So "\u09AC\u09BE\u0982\u09B2\u09BE" (one Bangla word with kar) and "\u0995\u09CD\u09B7" (conjunct with hasanta) are ONE word.
 *  - A LINE ends at "\n" (the "\n" is not part of the line). "\r" is treated as a normal character except that it is
 *    kept out of a selected line when it sits right before "\n".
 *  - null result = nothing to do (the caller does nothing).
 */
public final class EditOps {
    private EditOps() { }

    /** Result of {@link #duplicateLine}: insert [text] at [pos] (pos is inside the string given to the method). */
    public static final class Dup {
        public final int pos;
        public final String text;
        /** Where the cursor should go after the insert (inside the string AFTER the insert): end of the copy. */
        public final int cursor;
        Dup(int pos, String text, int cursor) { this.pos = pos; this.text = text; this.cursor = cursor; }
    }

    // ------------------------------------------------------------------ helpers

    private static String nz(String t) { return t == null ? "" : t; }

    private static int[] clamp(String t, int s, int e) {
        int n = t.length();
        if (s < 0) s = 0;
        if (e < 0) e = 0;
        if (s > n) s = n;
        if (e > n) e = n;
        if (s > e) { int x = s; s = e; e = x; }
        return new int[] { s, e };
    }

    /** True for a character that belongs to a word (code point version, so letters outside the BMP work). */
    public static boolean isWord(int cp) {
        if (cp == '_' || cp == 0x200C || cp == 0x200D) return true;
        if (Character.isLetterOrDigit(cp)) return true;
        int ty = Character.getType(cp);
        return ty == Character.NON_SPACING_MARK || ty == Character.COMBINING_SPACING_MARK || ty == Character.ENCLOSING_MARK;
    }

    /** Index of the start of the code point that ends at [i] (i > 0). */
    private static int prevIdx(String t, int i) {
        return i - Character.charCount(Character.codePointBefore(t, i));
    }

    /** Move an index that sits between a high and a low surrogate to the start of the pair. */
    private static int fixLeft(String t, int i) {
        if (i > 0 && i < t.length() && Character.isHighSurrogate(t.charAt(i - 1)) && Character.isLowSurrogate(t.charAt(i))) return i - 1;
        return i;
    }

    /** Move an index that sits between a high and a low surrogate to the end of the pair. */
    private static int fixRight(String t, int i) {
        if (i > 0 && i < t.length() && Character.isHighSurrogate(t.charAt(i - 1)) && Character.isLowSurrogate(t.charAt(i))) return i + 1;
        return i;
    }

    /** Start of the line that holds position [p] (just after the previous "\n", or 0). */
    private static int lineStartOf(String t, int p) {
        int i = p;
        while (i > 0 && t.charAt(i - 1) != '\n') i--;
        return i;
    }

    /** End of the line that holds position [p] (index of the next "\n", or length). */
    private static int lineEndOf(String t, int p) {
        int n = t.length();
        int i = p;
        while (i < n && t.charAt(i) != '\n') i++;
        return i;
    }

    // ------------------------------------------------------------------ select

    /**
     * Select word: the word that holds the cursor. If the cursor sits at the end of a word (next char is not a word char)
     * the word on the LEFT is used; if the cursor sits before a word, the word on the right. A selection that is not
     * empty is first collapsed to its start (so the word under the start of the selection is taken).
     * Returns {start, end} or null when there is no word next to the cursor (empty text, only spaces / symbols).
     */
    public static int[] selectWord(String text, int selStart, int selEnd) {
        String t = nz(text);
        int[] r = clamp(t, selStart, selEnd);
        int p = r[0];
        int n = t.length();
        boolean rightWord = p < n && isWord(t.codePointAt(p));
        boolean leftWord = p > 0 && isWord(t.codePointBefore(p));
        if (!rightWord && !leftWord) return null;
        int a = p, b = p;
        while (a > 0 && isWord(t.codePointBefore(a))) a = prevIdx(t, a);
        while (b < n && isWord(t.codePointAt(b))) b += Character.charCount(t.codePointAt(b));
        if (a == b) return null;
        return new int[] { a, b };
    }

    /**
     * Select line: from the start of the line that holds selStart to the end of the line that holds selEnd (several lines
     * when the selection spans lines). The line break itself is NOT selected. If the selection already ends exactly
     * after a "\n" that "\n" line is not added. Empty line = {p, p} (caret stays, nothing to select) is returned as null.
     */
    public static int[] selectLine(String t0, int selStart, int selEnd) {
        String t = nz(t0);
        int[] r = clamp(t, selStart, selEnd);
        int s = r[0], e = r[1];
        if (e > s && t.charAt(e - 1) == '\n') e--;   // selection ended right after a line break
        int a = lineStartOf(t, s);
        int b = lineEndOf(t, e);
        if (b > a && t.charAt(b - 1) == '\r') b--;
        if (b <= a) return null;
        return new int[] { a, b };
    }

    /** Select none: collapse to the END of the selection (like most editors). Returns {e, e}, or null when nothing is selected. */
    public static int[] collapse(String t0, int selStart, int selEnd) {
        String t = nz(t0);
        int[] r = clamp(t, selStart, selEnd);
        if (r[0] == r[1]) return null;
        return new int[] { r[1], r[1] };
    }

    // ------------------------------------------------------------------ cursor moves

    /** Cursor to the start of the line that holds the cursor (selection start is used). Returns the new cursor, or -1 when already there. */
    public static int lineStart(String t0, int selStart, int selEnd) {
        String t = nz(t0);
        int[] r = clamp(t, selStart, selEnd);
        int a = lineStartOf(t, r[0]);
        return (a == r[0] && r[0] == r[1]) ? -1 : a;
    }

    /** Cursor to the end of the line that holds the cursor (selection end is used; "\r" before "\n" stays outside). Returns the new cursor, or -1 when already there. */
    public static int lineEnd(String t0, int selStart, int selEnd) {
        String t = nz(t0);
        int[] r = clamp(t, selStart, selEnd);
        int b = lineEndOf(t, r[1]);
        if (b > r[1] && t.charAt(b - 1) == '\r') b--;
        return (b == r[1] && r[0] == r[1]) ? -1 : b;
    }

    // ------------------------------------------------------------------ delete

    /**
     * Delete word: a non-empty selection = that selection. Otherwise the same rule as hold-backspace (WordDelete.count) on the
     * text before the cursor. Returns the range to delete {start, end} or null (nothing before the cursor).
     */
    public static int[] deleteWord(String t0, int selStart, int selEnd) {
        String t = nz(t0);
        int[] r = clamp(t, selStart, selEnd);
        if (r[1] > r[0]) return new int[] { fixLeft(t, r[0]), fixRight(t, r[1]) };
        int from = Math.max(0, r[0] - WordDelete.WINDOW);
        from = fixRight(t, from);
        int c = WordDelete.count(t.substring(from, r[0]));
        if (c <= 0) return null;
        int a = r[0] - c;
        a = fixLeft(t, a);
        return new int[] { a, r[0] };
    }

    /**
     * Delete line: the whole line (or all lines touched by the selection) plus ONE line break: the one after the last
     * line, or, when the last line has none (end of text), the one before the first line. So no empty line is left behind.
     * Returns {start, end} or null when the text is empty.
     */
    public static int[] deleteLine(String t0, int selStart, int selEnd) {
        String t = nz(t0);
        if (t.length() == 0) return null;
        int[] r = clamp(t, selStart, selEnd);
        int s = r[0], e = r[1];
        if (e > s && t.charAt(e - 1) == '\n') e--;
        int a = lineStartOf(t, s);
        int b = lineEndOf(t, e);
        if (b < t.length()) b++;                                 // take the "\n" after the last line
        else if (a > 0) a--;                                    // last line of the text: take the "\n" before the first line
        if (b <= a) return null;
        return new int[] { a, b };
    }

    // ------------------------------------------------------------------ duplicate

    /**
     * Duplicate line: copy the line (or all lines touched by the selection) below itself. The insert is "\n" + the lines,
     * placed at the end of the last line. {@link Dup#cursor} = end of the copy (in the string after the insert).
     * An empty line (or an empty text) duplicates as an empty line, so the result is never null.
     */
    public static Dup duplicateLine(String t0, int selStart, int selEnd) {
        String t = nz(t0);
        int[] r = clamp(t, selStart, selEnd);
        int s = r[0], e = r[1];
        if (e > s && t.charAt(e - 1) == '\n') e--;
        int a = lineStartOf(t, s);
        int b = lineEndOf(t, e);
        String lines = t.substring(a, b);
        String ins = "\n" + lines;
        return new Dup(b, ins, b + ins.length());
    }
}
