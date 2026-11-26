package com.mgb.keyboard.typing;

/**
 * v0.51.0: pure rules of the cursor pad's live status line ("Ln 2, Col 7 . 12 chars . 3 words selected").
 * Pure Java (no Android classes), same style as PadState / PadGesture, so it can be tested on a computer.
 */
public final class PadInfo {
    private PadInfo() { }

    /** Value of a number that could not be worked out (the app does not show the text). */
    public static final int UNKNOWN = -1;

    /** Words = runs of letters / digits / marks / underscore (same idea as the Word keys). */
    public static int words(CharSequence s) {
        if (s == null) return 0;
        int n = 0;
        boolean in = false;
        for (int i = 0; i < s.length(); ) {
            int cp = Character.codePointAt(s, i);
            i += Character.charCount(cp);
            boolean w = Character.isLetterOrDigit(cp) || cp == '_' || isMark(cp);
            if (w) { if (!in) n++; in = true; } else in = false;
        }
        return n;
    }

    private static boolean isMark(int cp) {
        int t = Character.getType(cp);
        return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK || t == Character.ENCLOSING_MARK;
    }

    /**
     * @param text  the whole field text, or null when only a part is known
     * @param start selection start (absolute), end = selection end (absolute); the caret side is end
     * @return {line (1-based) or UNKNOWN, column (1-based) or UNKNOWN, selected code points, selected words}
     */
    public static int[] compute(CharSequence text, int start, int end) {
        int a = Math.min(start, end), b = Math.max(start, end);
        if (text == null || a < 0 || b > text.length()) return new int[] { UNKNOWN, UNKNOWN, 0, 0 };
        CharSequence sel = text.subSequence(a, b);
        int caret = end;
        int line = 1, ls = 0;
        for (int i = 0; i < caret; i++) if (text.charAt(i) == '\n') { line++; ls = i + 1; }
        int col = text.subSequence(ls, caret).toString().codePointCount(0, caret - ls) + 1;
        return new int[] { line, col, sel.toString().codePointCount(0, sel.length()), words(sel) };
    }

    /** Status text. info = compute() result (or null = nothing known): "Ln 2, Col 7" + (selection ? "  .  12 chars, 3 words selected"). */
    public static String text(int[] info) {
        if (info == null) return "";
        StringBuilder sb = new StringBuilder();
        if (info[0] != UNKNOWN && info[1] != UNKNOWN) sb.append("Ln ").append(info[0]).append(", Col ").append(info[1]);
        else if (info[1] != UNKNOWN) sb.append("Col ").append(info[1]);   // the app shows only a part of the text: no line number
        if (info[2] > 0) {
            if (sb.length() > 0) sb.append("  \u00b7  ");
            sb.append(info[2]).append(info[2] == 1 ? " char" : " chars").append(", ").append(info[3]).append(info[3] == 1 ? " word" : " words").append(" selected");
        }
        return sb.toString();
    }
}
