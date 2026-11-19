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

    /**
     * v0.42.5: more edits than the field can take one by one (each edit = 3 calls to the app) used to mean "rewrite the WHOLE text" = the format of the whole field lost.
     * Now the closest edits are joined into bigger ones (found = the old text between them, repl = the new text between them), so at most [max] edits remain and the
     * text far from the matches keeps its format. Only the small gaps that were joined are rewritten. Returns the same list when it is short enough; null on any problem.
     */
    public static List<ReplaceEngine.Hit> coalesce(String before, List<ReplaceEngine.Hit> edits, int max) {
        try {
            if (before == null || edits == null || max < 1) return null;
            int n = edits.size();
            if (n <= max) return edits;
            int need = n - max;   // gaps to close
            Integer[] idx = new Integer[n - 1];
            for (int i = 0; i < n - 1; i++) idx[i] = i;
            final List<ReplaceEngine.Hit> e = edits;
            java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
                public int compare(Integer x, Integer y) {
                    int gx = e.get(x + 1).start - e.get(x).end, gy = e.get(y + 1).start - e.get(y).end;
                    return gx != gy ? (gx < gy ? -1 : 1) : (x < y ? -1 : (x > y ? 1 : 0));
                }
            });
            boolean[] join = new boolean[n - 1];
            for (int k = 0; k < need; k++) join[idx[k]] = true;
            List<ReplaceEngine.Hit> out = new ArrayList<ReplaceEngine.Hit>(max);
            int i = 0;
            while (i < n) {
                int j = i;
                while (j < n - 1 && join[j]) j++;
                if (j == i) { out.add(edits.get(i)); }
                else {
                    int a = edits.get(i).start, b = edits.get(j).end;
                    StringBuilder sb = new StringBuilder();
                    int pos = a;
                    for (int k = i; k <= j; k++) {
                        ReplaceEngine.Hit h = edits.get(k);
                        sb.append(before, pos, h.start).append(h.repl);
                        pos = h.end;
                    }
                    out.add(new ReplaceEngine.Hit(a, b, before.substring(a, b), sb.toString()));
                }
                i = j + 1;
            }
            return out;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
