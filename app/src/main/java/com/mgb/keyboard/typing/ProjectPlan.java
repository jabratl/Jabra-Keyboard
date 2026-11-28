package com.mgb.keyboard.typing;

/**
 * v0.66.0 (PROJECT 3): pure rules of the Project screen (design = docs/project-screen-mock.html).
 * No Android here (tested by tools/ProjectPlanTest.java). Never throws, never returns null.
 *  - window(): the short "Field text" preview (a few lines around the first match, highlights cut to fit)
 *  - labels: "N to fix", "N in text", "Fix N in text", "N terms", the hint line, the project chip
 *  - listHeightDp(): how tall the term list is, from the user's key height (so a bigger keyboard gets a longer list)
 */
public final class ProjectPlan {
    private ProjectPlan() { }

    public static final int PREVIEW_MAX = 100;      // characters shown in the preview (2 lines)
    public static final int LIST_MIN_DP = 56, LIST_MAX_DP = 236;
    public static final int CHIP_MAX = 18;          // characters of the project name on the chip

    /** The preview text and the highlight spans inside it (starts[i] .. ends[i], end exclusive). */
    public static final class Window {
        public String text = "";
        public int[] starts = new int[0];
        public int[] ends = new int[0];
        public boolean cutStart, cutEnd;
    }

    /**
     * A window of at most [max] characters of [text] that shows the first span (when there is one). New lines and tabs become spaces
     * (one for one, so spans stay valid). A cut at the start / end adds an ellipsis. Spans outside the window are dropped, partly
     * outside are clipped. Bad input gives an empty window.
     */
    public static Window window(String text, int[] starts, int[] ends, int max) {
        Window w = new Window();
        try {
            if (text == null || text.isEmpty()) return w;
            if (max < 20) max = 20;
            int n = text.length();
            int cnt = 0;
            if (starts != null && ends != null) cnt = Math.min(starts.length, ends.length);
            int first = -1;
            for (int i = 0; i < cnt; i++) {
                if (starts[i] < 0 || ends[i] <= starts[i] || ends[i] > n) continue;
                if (first < 0 || starts[i] < starts[first]) first = i;
            }
            int begin = 0;
            if (n > max && first >= 0) {
                begin = Math.max(0, starts[first] - max / 4);
                // move to a word start (look at most 12 characters ahead, never past the first match)
                if (begin > 0) {
                    int lim = Math.min(starts[first], begin + 12);
                    for (int i = begin; i < lim; i++) { if (Character.isWhitespace(text.charAt(i - 1))) { begin = i; break; } }
                }
            }
            if (begin > 0 && begin < n && Character.isLowSurrogate(text.charAt(begin))) begin--;
            int end = Math.min(n, begin + max);
            if (end < n) {
                int floor = first >= 0 ? Math.min(end, ends[first]) : begin;
                for (int i = end; i > Math.max(floor, end - 12); i--) { if (Character.isWhitespace(text.charAt(i - 1))) { end = i - 1; break; } }
                if (end > begin && end < n && Character.isLowSurrogate(text.charAt(end))) end--;
            }
            w.cutStart = begin > 0;
            w.cutEnd = end < n;
            StringBuilder sb = new StringBuilder();
            if (w.cutStart) sb.append('\u2026');
            int shift = (w.cutStart ? 1 : 0) - begin;
            for (int i = begin; i < end; i++) {
                char c = text.charAt(i);
                sb.append(c == '\n' || c == '\r' || c == '\t' ? ' ' : c);
            }
            if (w.cutEnd) sb.append('\u2026');
            w.text = sb.toString();
            int[] s2 = new int[cnt], e2 = new int[cnt];
            int k = 0;
            for (int i = 0; i < cnt; i++) {
                int a = starts[i], b = ends[i];
                if (a < 0 || b <= a || b > n) continue;
                if (b <= begin || a >= end) continue;
                a = Math.max(a, begin); b = Math.min(b, end);
                s2[k] = a + shift; e2[k] = b + shift; k++;
            }
            w.starts = java.util.Arrays.copyOf(s2, k);
            w.ends = java.util.Arrays.copyOf(e2, k);
        } catch (Throwable t) {
            return new Window();
        }
        return w;
    }

    /** Top right of the preview card. hits &lt; 0 = not counted (text too long). */
    public static String headCount(int hits) {
        if (hits < 0) return "";
        return hits == 0 ? "nothing to fix" : hits + " to fix";
    }

    /** The yellow badge on a term: "" when it is not in the text. */
    public static String inText(int n) { return n > 0 ? n + " in text" : ""; }

    public static String termsLabel(int n) { return n + (n == 1 ? " term" : " terms"); }

    /** The Fix button text. hits &lt; 0 = not counted. */
    public static String fixLabel(int hits, boolean hasTerms) {
        if (hits < 0) return hasTerms ? "Fix with project" : "Nothing to fix";
        return hits > 0 ? "Fix " + hits + " in text" : "Nothing to fix";
    }

    public static boolean fixEnabled(int hits, boolean hasTerms) { return hasTerms && hits != 0; }

    /** The grey line under the Add box: a fresh message first, else the editing note, else the rule. */
    public static String hint(boolean editing, String note) {
        if (note != null && !note.trim().isEmpty()) return note.trim();
        return editing ? "Editing a term. Tap Save, or \u2715 to cancel." : "One line per term. Separate wrong forms with ;";
    }

    /** The project name on the chip, cut with an ellipsis. */
    public static String chipName(String name) {
        if (name == null) return "";
        String n = name.trim();
        if (n.length() <= CHIP_MAX) return n;
        int cut = CHIP_MAX - 1;
        if (cut > 0 && Character.isHighSurrogate(n.charAt(cut - 1))) cut--;
        return n.substring(0, cut) + "\u2026";
    }

    /** Height of the term list in dp. About six key rows in all, minus what the other lines use (the screen must not be taller than the keyboard area); kept between LIST_MIN_DP and LIST_MAX_DP. */
    public static int listHeightDp(int keyHeightDp) {
        int k = keyHeightDp <= 0 ? 48 : keyHeightDp;
        int h = k * 6 - 232;
        return h < LIST_MIN_DP ? LIST_MIN_DP : (h > LIST_MAX_DP ? LIST_MAX_DP : h);
    }

    /** True when a message is a problem (it is drawn in the warning colour). */
    public static boolean isProblem(String note) {
        if (note == null) return false;
        String n = note.trim();
        String[] bad = { "Type ", "Use:", "Pick ", "Could not", "Not available", "Nothing", "The list is full", "Already kept", "Cannot" };
        for (String b : bad) { if (n.startsWith(b)) return true; }
        return false;
    }

    /** True for the message of a finished fix (it gets the Undo bar). */
    public static boolean isFixed(String note) { return note != null && note.trim().startsWith("Fixed"); }

    /** The message as shown: cut before ". Tap Undo", and "Fixed 5" becomes "Fixed 5 in the text". */
    public static String toastText(String raw) {
        if (raw == null) return "";
        String t = raw;
        int i = t.indexOf(". Tap Undo");
        if (i >= 0) t = t.substring(0, i);
        t = t.trim();
        if (t.matches("Fixed \\d+")) t = t + " in the text";
        return t;
    }
}
