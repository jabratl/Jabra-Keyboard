package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.42.5: Find & Replace with a PAIR LIST (a rule set) runs many rules, each on the result of the one before. Only ONE-rule runs had an edit list,
 * so a pair list was written back as the WHOLE text (select all + commit) and the format of the field (bold / italic / links / paragraphs) was lost,
 * and Copy could not keep the format either. This class joins the edit lists of the single rules into ONE edit list on the ORIGINAL text:
 *   compose(orig, first, second) : first = edits orig -> mid (ascending, not overlapping), second = edits mid -> end.
 *   Result = edits orig -> end (ascending, not overlapping), so ReplaceEdits.apply(orig, result) == the end text.
 * Edits of the second step that touch text made by the first step are merged into ONE edit (found = the original text of the merged range).
 * Net no-ops (cat > dog, then dog > cat) are dropped. PURE JAVA (no Android), never throws (any problem = null: the caller keeps the old whole-text replace).
 * Test: tools/ReplaceComposeTest.java.
 */
public final class ReplaceCompose {
    private ReplaceCompose() { }

    /** true when the text of the two intervals must be handled together (they overlap, or one of them is empty and sits inside / on the edge of the other). */
    private static boolean joins(int curStart, int curEnd, int s, int e) {
        if (s < curEnd) return true;                       // overlap (the list is sorted by start, so s >= curStart)
        if (s > curEnd) return false;                      // a gap
        return curEnd == curStart || s == e;               // touching: only when one side is empty (an insertion / a deletion)
    }

    /** edits orig -> end, or null when something is not usable (overlapping input, wrong offsets). */
    public static List<ReplaceEngine.Hit> compose(String orig, List<ReplaceEngine.Hit> first, List<ReplaceEngine.Hit> second) {
        try {
            if (orig == null || first == null || second == null) return null;
            if (second.isEmpty()) return new ArrayList<ReplaceEngine.Hit>(first);
            if (first.isEmpty()) return new ArrayList<ReplaceEngine.Hit>(second);
            String mid = ReplaceEdits.apply(orig, first);

            // the first step's edits, in MID coordinates
            int n = first.size();
            int[] cs = new int[n], ce = new int[n], dl = new int[n];
            int delta = 0, prevEnd = 0;
            for (int i = 0; i < n; i++) {
                ReplaceEngine.Hit h = first.get(i);
                if (h.start < prevEnd || h.end < h.start || h.end > orig.length()) return null;
                prevEnd = h.end;
                cs[i] = h.start + delta;
                ce[i] = cs[i] + h.repl.length();
                dl[i] = h.repl.length() - (h.end - h.start);
                delta += dl[i];
            }
            int m = second.size();
            int prev2 = 0;
            for (int j = 0; j < m; j++) {
                ReplaceEngine.Hit h = second.get(j);
                if (h.start < prev2 || h.end < h.start || h.end > mid.length()) return null;
                prev2 = h.end;
            }

            List<ReplaceEngine.Hit> out = new ArrayList<ReplaceEngine.Hit>(n + m);
            int i = 0, j = 0;
            int before = 0;   // total length change of all first-step edits that are completely before the current cluster (orig = mid - before)
            while (i < n || j < m) {
                // start a cluster with the interval that starts first
                int cStart, cEnd;
                int i0 = i, j0 = j;
                if (j >= m || (i < n && cs[i] <= second.get(j).start)) { cStart = cs[i]; cEnd = ce[i]; i++; }
                else { cStart = second.get(j).start; cEnd = second.get(j).end; j++; }
                boolean grew = true;
                while (grew) {
                    grew = false;
                    if (i < n && joins(cStart, cEnd, cs[i], ce[i])) { cEnd = Math.max(cEnd, ce[i]); i++; grew = true; }
                    if (j < m && joins(cStart, cEnd, second.get(j).start, second.get(j).end)) { cEnd = Math.max(cEnd, second.get(j).end); j++; grew = true; }
                }
                int inner = 0;
                for (int k = i0; k < i; k++) inner += dl[k];
                int a = cStart - before;
                int b = cEnd - (before + inner);
                if (a < 0 || b < a || b > orig.length()) return null;
                // the end text of this cluster = mid[cStart, cEnd) with the second-step edits applied
                StringBuilder sb = new StringBuilder();
                int pos = cStart;
                for (int k = j0; k < j; k++) {
                    ReplaceEngine.Hit h = second.get(k);
                    sb.append(mid, pos, h.start).append(h.repl);
                    pos = h.end;
                }
                sb.append(mid, pos, cEnd);
                String found = orig.substring(a, b);
                String repl = sb.toString();
                if (!found.equals(repl)) out.add(new ReplaceEngine.Hit(a, b, found, repl));   // a net no-op is not an edit
                before += inner;
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
