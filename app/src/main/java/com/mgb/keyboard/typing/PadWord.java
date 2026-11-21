package com.mgb.keyboard.typing;

import static java.lang.Character.COMBINING_SPACING_MARK;
import static java.lang.Character.ENCLOSING_MARK;
import static java.lang.Character.NON_SPACING_MARK;

/**
 * v0.45.3: word-jump rule for the cursor pad keys "Word left" / "Word right" (used only when the app refuses the Ctrl+Left / Ctrl+Right key event).
 * Left = skip separators backwards, then one run of word chars backwards. Right = skip separators forward, then one run of word chars forward.
 * Word char = letter / digit / "_" AND the combining marks (Bangla vowel signs and the like must NOT split a word). Line breaks count as separators.
 * Pure Java (no Android classes), unit tested on a computer.
 */
public final class PadWord {
    private PadWord() { }

    /** Ask the field for this many chars before / after the cursor. */
    public static final int WINDOW = 200;

    /** New cursor position inside w (0..w.length()) for one word step from pos. null window = pos unchanged. */
    public static int step(CharSequence w, int pos, boolean left) {
        if (w == null) return pos;
        int n = w.length();
        int p = pos < 0 ? 0 : (pos > n ? n : pos);
        if (left) {
            while (p > 0 && !isWord(w.charAt(p - 1))) p--;
            while (p > 0 && isWord(w.charAt(p - 1))) p--;
        } else {
            while (p < n && !isWord(w.charAt(p))) p++;
            while (p < n && isWord(w.charAt(p))) p++;
        }
        return p;
    }

    static boolean isWord(char c) {
        if (Character.isLetterOrDigit(c) || c == '_') return true;
        int t = Character.getType(c);
        return t == NON_SPACING_MARK || t == COMBINING_SPACING_MARK || t == ENCLOSING_MARK;
    }
}
