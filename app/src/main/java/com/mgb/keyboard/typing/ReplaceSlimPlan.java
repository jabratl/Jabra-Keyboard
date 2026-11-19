package com.mgb.keyboard.typing;

/**
 * v0.42.2 (M2 of PLAN_RP_MINIMAL_M2-M4): pure rules of the slim Find & Replace look (step row, note line, pair button).
 * No Android here (tested by tools/ReplaceSlimPlanTest.java). Never throws, never returns null.
 */
public final class ReplaceSlimPlan {
    private ReplaceSlimPlan() { }

    public static final int R_NONE = 0, R_UNDO = 1, R_PASTE_BACK = 2, R_COPY = 3;

    /** What the slim step row shows. */
    public static final class Step {
        public boolean nav, rest, one;       // arrows + counter / Rest / This visible
        public boolean navEnabled;           // arrows / Rest / This clickable
        public String counter = "0";         // "k / N" or "0"
        public String allLabel = "Replace all";
        public boolean allEnabled;
    }

    public static Step step(int nHits, int count, int step, boolean err, boolean blank, boolean setLoaded, boolean pairMode,
                            boolean findEmpty, int nPairs, boolean ask) {
        Step s = new Step();
        String all = ask ? "Sure? Replace all" : "Replace all";
        if (pairMode && findEmpty) {   // a pair list and nothing typed in Find: only Replace all (N pairs)
            int n = Math.max(0, nPairs);
            s.allLabel = all + " (" + n + (n == 1 ? " pair)" : " pairs)");
            s.allEnabled = n > 0;
            return s;
        }
        if (setLoaded && !pairMode) {  // a loaded set: only Replace all
            s.allLabel = all;
            s.allEnabled = !err;
            return s;
        }
        s.nav = true; s.rest = true; s.one = true;
        boolean hits = nHits > 0 && !err;
        s.navEnabled = hits;
        if (hits) {
            int c = Math.max(count, nHits);
            int k = Math.max(0, Math.min(step, nHits - 1)) + 1;
            s.counter = k + " / " + c;
        }
        s.allLabel = all;
        s.allEnabled = !err && count > 0 && !blank;
        return s;
    }

    /** The note line: left text (tap = scope) and the link on the right (undo > paste back > copy). */
    public static final class Note {
        public String left = "";
        public boolean leftIsScope;   // true = the default scope text (tappable); false = a message
        public int rightKind = R_NONE;
        public String rightLabel = "";
    }

    public static Note note(String msg, String scopeWord, int chars, boolean canUndo, String undoLabel, boolean canPasteBack) {
        Note n = new Note();
        String m = msg == null ? "" : msg;
        if (m.length() > 0) { n.left = m; n.leftIsScope = false; }
        else { n.left = (scopeWord == null ? "" : scopeWord.trim()) + ": " + Math.max(0, chars) + " characters"; n.leftIsScope = true; }
        if (canUndo) { n.rightKind = R_UNDO; n.rightLabel = (undoLabel == null || undoLabel.length() == 0) ? "Undo" : undoLabel; }
        else if (canPasteBack) { n.rightKind = R_PASTE_BACK; n.rightLabel = "Paste back"; }
        else { n.rightKind = R_COPY; n.rightLabel = "Copy"; }
        return n;
    }

    /** Top bar pair button: "Update" | "Pair" | "+Paste" | "" (same conditions as the old code). */
    public static String pairButton(boolean pairMode, boolean setLoaded, boolean findEmpty, int editIdx) {
        if (pairMode || (!setLoaded && !findEmpty)) {
            if (findEmpty) return pairMode ? "Pair" : "";   // old code: editIdx reset when Find is empty
            return editIdx >= 0 ? "Update" : "Pair";
        }
        if (!setLoaded) return "+Paste";
        return "";
    }
}
