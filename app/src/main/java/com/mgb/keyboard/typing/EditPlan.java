package com.mgb.keyboard.typing;

/**
 * v0.15.8 (N8) pure helpers for the easy-edit polish of the app pages (no Android).
 *  - parseValue: the number typed into a stepper dialog -> an int clamped to the row range (null = nothing usable typed).
 *  - changedIndexes: which rows a "Reset this section / page" must touch (only rows that differ from default).
 *  - doneText / undoText: the one-line texts of the reset toast and of the Undo button.
 */
public final class EditPlan {
    private EditPlan() { }

    /**
     * Reads the first whole number of [text] (optional leading "-", digits of any script, so Bangla digits work too).
     * Anything after the number (a unit like "%" or " dp", a decimal part) is ignored. Result is clamped to [min, max].
     * No digit at all (empty, null, "abc", "-") = null. min > max is treated as swapped.
     */
    public static Integer parseValue(String text, int min, int max) {
        if (text == null) return null;
        int lo = Math.min(min, max), hi = Math.max(min, max);
        String s = text.trim();
        int i = 0, n = s.length();
        boolean neg = false;
        if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '\u2212')) { neg = true; i++; }
        long v = 0;
        int digits = 0;
        while (i < n) {
            int d = Character.digit(s.charAt(i), 10);
            if (d < 0) break;
            digits++;
            if (v < 100000000L) v = v * 10 + d;   // cap: a huge number just ends up at the range limit
            i++;
        }
        if (digits == 0) return null;
        long r = neg ? -v : v;
        if (r < lo) r = lo;
        if (r > hi) r = hi;
        return (int) r;
    }

    /** Indexes i where cur[i] is a saved value different from def[i] (same rule as the changed dot). Bad / null input = none. */
    public static int[] changedIndexes(int[] cur, int[] def) {
        if (cur == null || def == null) return new int[0];
        int n = Math.min(cur.length, def.length);
        int[] tmp = new int[n];
        int c = 0;
        for (int i = 0; i < n; i++) if (ChangedPlan.isChanged(cur[i], def[i])) tmp[c++] = i;
        int[] out = new int[c];
        System.arraycopy(tmp, 0, out, 0, c);
        return out;
    }

    /** Toast after a reset. */
    public static String doneText(int n) {
        if (n <= 0) return "Nothing to reset: all settings here are already default";
        return "Reset " + n + (n == 1 ? " setting" : " settings");
    }

    /** Text of the Undo button after a reset, e.g. "Undo reset: Spacing (3 settings)". */
    public static String undoText(String name, int n) {
        String w = (name == null || name.trim().isEmpty()) ? "this page" : name.trim();
        return "Undo reset: " + w + " (" + n + (n == 1 ? " setting" : " settings") + ")";
    }
}
