package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.28.0 (R3b) small pure helpers of the Find & Replace panel. No Android, never throws.
 *  - UndoStack: the last MAX Replace taps (result text + original text), memory only, newest on top. Never written to disk.
 *  - formatterText(): a rule set as text for the Formatter's Rules page ("Rules from clipboard"). Same syntax, so only a comment header is added.
 */
public final class ReplaceExtras {
    private ReplaceExtras() { }

    public static final int MAX_UNDO = 3;

    public static final class Step {
        public final String res, orig;
        Step(String res, String orig) { this.res = res == null ? "" : res; this.orig = orig == null ? "" : orig; }
    }

    public static final class UndoStack {
        private final List<Step> l = new ArrayList<Step>();

        /** Adds a replace. A step that changed nothing (res equals orig) is ignored. The oldest step is dropped above MAX_UNDO. */
        public void push(String res, String orig) {
            if (res == null || orig == null || res.equals(orig)) return;
            l.add(new Step(res, orig));
            while (l.size() > MAX_UNDO) l.remove(0);
        }

        public boolean canUndo() { return !l.isEmpty(); }
        public int size() { return l.size(); }

        /** The newest step (stays in the stack until drop()), or null. */
        public Step peek() { return l.isEmpty() ? null : l.get(l.size() - 1); }

        /** Removes the newest step (after a successful undo). */
        public void drop() { if (!l.isEmpty()) l.remove(l.size() - 1); }

        public void clear() { l.clear(); }
    }

    /** First [n] chars, never cutting a surrogate pair in half (emoji, rare Chinese / Japanese characters). null = "". n <= 0 = "". */
    public static String head(String s, int n) {
        if (s == null || n <= 0) return "";
        if (s.length() <= n) return s;
        int e = n;
        if (Character.isHighSurrogate(s.charAt(e - 1)) && Character.isLowSurrogate(s.charAt(e))) e--;
        return s.substring(0, e);
    }

    /** Last [n] chars, never cutting a surrogate pair in half. null = "". n <= 0 = "". */
    public static String tail(String s, int n) {
        if (s == null || n <= 0) return "";
        if (s.length() <= n) return s;
        int b = s.length() - n;
        if (Character.isLowSurrogate(s.charAt(b)) && Character.isHighSurrogate(s.charAt(b - 1))) b++;
        return s.substring(b);
    }

    /** Label of the Undo button: "Undo" for one step, "Undo (2)" for more. */
    public static String undoLabel(int n) { return n > 1 ? "Undo (" + n + ")" : "Undo"; }

    /**
     * The set as Formatter rules text. Header = 2 comment lines (the Formatter and the sets both skip # lines).
     * The Formatter has its own switches, so the header says which flags the set used. Empty / null rules = "" (nothing to copy).
     */
    public static String formatterText(String name, String rulesText, boolean matchCase, boolean word) {
        if (rulesText == null || rulesText.trim().isEmpty()) return "";
        String n = name == null ? "" : name.replace('\n', ' ').replace('\r', ' ').trim();
        StringBuilder b = new StringBuilder();
        b.append("# From Find & Replace set: ").append(n.isEmpty() ? "(no name)" : n).append('\n');
        b.append("# Set options: match case ").append(matchCase ? "ON" : "OFF").append(", whole word ").append(word ? "ON" : "OFF").append('\n');
        b.append(rulesText.endsWith("\n") ? rulesText : rulesText + "\n");
        return b.toString();
    }
}
