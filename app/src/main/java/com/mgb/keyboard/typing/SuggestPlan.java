package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.10.1 (G2) the pure rules of the suggestion strip (no Android; tested by tools/SuggestPlanTest.java). The words come from WordIndex (G1).
 * - currentWord(): the word the cursor is at the end of (letters, Bangla signs, apostrophe inside the word).
 * - show(): the strip row REPLACES the toolbar row (like the reference keyboard) while pref sg_on is ON and the toolbar page is not opened (left icon opens it, the toolbar's first key comes back).
 * - layout(): [best suggestion, the word you typed (centre, tap = keep it + space), 2nd suggestion]; grid(): up to 18 words in 3 columns (long-press / pull down a slot, slide, release = pick).
 * - active(): no suggestions in password / email / URL / number fields (latinOnly) - the row stays (blank) so the height never jumps.
 * - slots(): always exactly 3 texts (blank when there is no word).
 * - toType(): what a tap types: the suggestion in the same capital style as typed + one space.
 */
public final class SuggestPlan {
    private SuggestPlan() { }

    public static final int SLOTS = 3;
    public static final int LOOKBACK = 40;     // chars read before the cursor
    public static final int MIN_CHARS = 1;

    private static boolean wordChar(char c) {
        if (Character.isLetter(c)) return true;
        int t = Character.getType(c);
        return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK;   // Bangla vowel signs, hasanta
    }

    /** The trailing word of [before] ("" when the text ends with a space / symbol / digit). A word may contain ' inside, never starts with it. */
    public static String currentWord(String before) {
        if (before == null || before.isEmpty()) return "";
        int end = before.length();
        int i = end;
        while (i > 0) {
            char c = before.charAt(i - 1);
            if (wordChar(c) || c == '\'' || c == '’') { i--; continue; }
            break;
        }
        // digits glued to the word = not a word (a1, 2nd)
        if (i > 0 && Character.isDigit(before.charAt(i - 1))) return "";
        while (i < end && (before.charAt(i) == '\'' || before.charAt(i) == '’')) i++;   // no leading quote
        String w = before.substring(i, end);
        if (w.endsWith("’")) return w;
        return w;
    }

    public static boolean show(boolean sgOn, boolean toolsOpen) { return sgOn && !toolsOpen; }

    public static final int COLS = 3;
    public static final int GRID_MAX = 18;
    public static final int GRID_ROWS_MAX = 6;
    public static final long LONG_MS = 350L;

    /** The 3 strip texts: [suggestion 1, typed word (centre), suggestion 2]. All blank when nothing is typed. */
    public static List<String> layout(String typed, List<String> sugg) {
        List<String> out = new ArrayList<String>(SLOTS);
        boolean has = typed != null && !typed.isEmpty();
        out.add(has && sugg != null && sugg.size() > 0 && sugg.get(0) != null ? sugg.get(0) : "");
        out.add(has ? typed : "");
        out.add(has && sugg != null && sugg.size() > 1 && sugg.get(1) != null ? sugg.get(1) : "");
        return out;
    }

    /** Words for the grid: the suggestions, at most GRID_MAX, no blanks. */
    public static List<String> grid(List<String> sugg) {
        List<String> out = new ArrayList<String>();
        if (sugg == null) return out;
        for (String w : sugg) { if (w != null && !w.isEmpty()) out.add(w); if (out.size() >= GRID_MAX) break; }
        return out;
    }

    public static int gridRows(int n) { return n <= 0 ? 0 : (n + COLS - 1) / COLS; }

    /** Grid cell under the finger (screen px), -1 = outside the grid or on an empty cell. */
    public static int gridIndex(float x, float y, float left, float top, float cellW, float cellH, int n) {
        if (n <= 0 || cellW <= 0f || cellH <= 0f) return -1;
        float dx = x - left, dy = y - top;
        if (dx < 0f || dy < 0f) return -1;
        int c = (int) (dx / cellW), r = (int) (dy / cellH);
        if (c >= COLS || r >= gridRows(n)) return -1;
        int i = r * COLS + c;
        return i < n ? i : -1;
    }

    public static boolean active(boolean pwdOrLatinOnly) { return !pwdOrLatinOnly; }

    /** Index language for the current typing: "bn" when Bangla is active and the field accepts it, else "en". */
    public static String lang(boolean bangla, boolean latinOnly) { return (bangla && !latinOnly) ? "bn" : "en"; }

    /** Exactly SLOTS texts: the words in order, then blanks. */
    public static List<String> slots(List<String> words) {
        List<String> out = new ArrayList<String>(SLOTS);
        for (int i = 0; i < SLOTS; i++) out.add(words != null && i < words.size() && words.get(i) != null ? words.get(i) : "");
        return out;
    }

    /** True when there is something to suggest for [word] (long enough). */
    public static boolean wantsLookup(String word) { return word != null && word.length() >= MIN_CHARS; }

    /** Text to type for a tap: matchCase(typed, suggestion) + " ". Blank suggestion = "" (nothing). */
    public static String toType(String typed, String suggestion) {
        if (suggestion == null || suggestion.isEmpty()) return "";
        return WordIndex.matchCase(typed, suggestion) + " ";
    }
}
