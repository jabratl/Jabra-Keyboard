package com.mgb.keyboard.typing;

/**
 * v0.17.58 (C3): the words of the NEW clipboard look (pref clip_new_look). Pure Java, no Android (tools/ClipCopyTest.java).
 * Title + count, empty / no-result texts, the "delete at once + Undo" rule. Never throws; null / negative input = safe text.
 */
public final class ClipCopy {
    private ClipCopy() { }

    /** Max clips deleted at once WITHOUT the "Sure?" tap (an Undo button is shown instead). More than this keeps "Sure?". */
    public static final int QUICK_DELETE_MAX = 2;

    private static int nz(int n) { return n < 0 ? 0 : n; }

    /** Header title: "Clipboard" or "Clipboard · 12". */
    public static String title(int total) {
        total = nz(total);
        return total == 0 ? "Clipboard" : "Clipboard \u00B7 " + total;
    }

    /** One quiet line: "12 clips · 3 pinned" / "1 clip" / "" when empty. */
    public static String countLine(int total, int pinned) {
        total = nz(total); pinned = nz(pinned);
        if (total == 0) return "";
        String s = total + (total == 1 ? " clip" : " clips");
        if (pinned > 0) s += " \u00B7 " + pinned + " pinned";
        return s;
    }

    /** Empty / no-result text (2 lines at most: what is empty + what to do). [query] only used while searching. */
    public static String empty(int chip, boolean searching, String query) {
        if (searching) {
            String q = query == null ? "" : query.trim();
            if (q.isEmpty()) return "Type to search your clips";
            if (q.length() > 20) q = q.substring(0, 20) + "\u2026";
            return "No result for \u201C" + q + "\u201D";
        }
        switch (ClipFilter.sanitize(chip)) {
            case ClipFilter.PINNED: return "No pinned clips yet\nTap \u2606 on a clip to keep it";
            case ClipFilter.TEXT: return "No text clips here";
            case ClipFilter.LINKS: return "No links copied yet";
            case ClipFilter.NUMBERS: return "No numbers copied yet";
            default: return "Nothing copied yet\nCopy any text and it appears here";
        }
    }

    /** True = delete [n] selected clips at once (Undo bar), false = keep the "Sure? delete n" tap. n < 1 = false. */
    public static boolean quickDelete(int n) { return n >= 1 && n <= QUICK_DELETE_MAX; }

    /** Message after a delete in the new look. */
    public static String deleted(int n) {
        n = nz(n);
        return "Deleted " + n + " \u00B7 tap Undo to restore";
    }

    /** Labels of the overflow tray (\u22EF) in the order they are shown. */
    public static final String[] MORE_LABELS = { "Manage", "Export all", "Clear" };
}
