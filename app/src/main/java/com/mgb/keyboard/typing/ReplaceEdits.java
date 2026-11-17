package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.36.2: Find & Replace changes the field MATCH BY MATCH (not the whole text), so bold / italic / links of the untouched text stay.
 * Pure Java (tools/ReplaceEditsTest.java): apply() = what the field text becomes, inverse() = the edits (in the NEW text) that bring the old text back (Undo).
 * Edits must be ascending and not overlapping (ReplaceEngine gives exactly that).
 */
public final class ReplaceEdits {
    private ReplaceEdits() { }

    public static String apply(String text, List<ReplaceEngine.Hit> edits) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int pos = 0;
        for (ReplaceEngine.Hit h : edits) {
            sb.append(text, pos, h.start);
            sb.append(h.repl);
            pos = h.end;
        }
        sb.append(text, pos, text.length());
        return sb.toString();
    }

    public static List<ReplaceEngine.Hit> inverse(List<ReplaceEngine.Hit> edits) {
        List<ReplaceEngine.Hit> inv = new ArrayList<ReplaceEngine.Hit>(edits.size());
        int delta = 0;
        for (ReplaceEngine.Hit h : edits) {
            int ns = h.start + delta;
            inv.add(new ReplaceEngine.Hit(ns, ns + h.repl.length(), h.repl, h.found));
            delta += h.repl.length() - (h.end - h.start);
        }
        return inv;
    }

    /** v0.37.1: the same edits moved by [by] characters (the edited piece starts at [by] inside the whole field). */
    public static List<ReplaceEngine.Hit> shift(List<ReplaceEngine.Hit> edits, int by) {
        List<ReplaceEngine.Hit> r = new ArrayList<ReplaceEngine.Hit>(edits.size());
        for (ReplaceEngine.Hit h : edits) r.add(new ReplaceEngine.Hit(h.start + by, h.end + by, h.found, h.repl));
        return r;
    }

    /** Safe to use: the edits really turn before into after (else the caller uses the old whole-text replace). */
    public static boolean matches(String before, String after, List<ReplaceEngine.Hit> edits) {
        try { return !edits.isEmpty() && apply(before, edits).equals(after); } catch (RuntimeException e) { return false; }
    }
}
