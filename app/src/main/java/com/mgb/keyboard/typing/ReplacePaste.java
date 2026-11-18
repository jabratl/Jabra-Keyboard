package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * v0.41.0 PASTE PAIRS (idea from the Bulk Replace tool, item 3). PURE JAVA (no Android), never throws.
 * parse(clipboardText, currentList, max) reads MANY pairs from pasted text and merges them into a copy of the current pair list.
 * One pair per line. Accepted forms (the first one found in the line is used):
 *   find => with            the Set / Formatter syntax, also  word: / keep: / smart: / re:  in front, and variants  a | b => c
 *   find [TAB] with         two columns copied from a sheet (more columns are ignored)
 *   find -> with   find → with   find = with   (with spaces around  =  and  ->  as a safety)
 * Empty lines and # comment lines are ignored (not counted). del: / del-re: lines and lines without a separator are skipped and counted (bad).
 * A line whose Find is already in the list (same kind + Find) replaces its With (updated), or is only a repeat (same). The list never goes over max: the rest is counted (full).
 * Every pair is made so that serialize() > parse() gives it back (same rule as ReplacePairList.make): a bad regex is skipped.
 * Limits: 500 lines are read, a line is cut off as bad above 300 characters.
 */
public final class ReplacePaste {
    private ReplacePaste() { }

    public static final int MAX_LINES = 500;
    public static final int MAX_LINE = 300;

    public static final class Result {
        public final List<ReplaceRules.Rule> rules = new ArrayList<ReplaceRules.Rule>();   // the new full list (current pairs + the pasted ones)
        public int added = 0, updated = 0, same = 0, bad = 0, full = 0;
        public int firstBad = 0;       // 1-based line number of the first bad line, 0 = none
        public boolean empty = false;  // nothing to read at all
        public int changed() { return added + updated; }

        /** One line for the panel: "Pasted 12 pairs (2 updated, 1 repeat, 3 lines skipped)". */
        public String summary() {
            if (empty) return "Nothing to paste: copy some lines first (cat => dog)";
            StringBuilder sb = new StringBuilder();
            int n = added + updated;
            if (n == 0) sb.append("No new pair found");
            else sb.append("Pasted ").append(n).append(n == 1 ? " pair" : " pairs");
            StringBuilder x = new StringBuilder();
            if (updated > 0) x.append(updated).append(" updated");
            if (same > 0) { if (x.length() > 0) x.append(", "); x.append(same).append(same == 1 ? " repeat" : " repeats"); }
            if (bad > 0) { if (x.length() > 0) x.append(", "); x.append(bad).append(bad == 1 ? " line skipped" : " lines skipped"); if (firstBad > 0) x.append(" (first: line ").append(firstBad).append(')'); }
            if (full > 0) { if (x.length() > 0) x.append(", "); x.append(full).append(" not added: the list is full"); }
            if (x.length() > 0) sb.append(" (").append(x).append(')');
            return sb.toString();
        }
    }

    /** One line in "find => with" form, or null when the line has no separator. */
    static String normalize(String line) {
        if (line.indexOf("=>") >= 0) return line;
        int k = line.indexOf('\t');
        if (k >= 0) {
            String a = line.substring(0, k).trim();
            String rest = line.substring(k + 1);
            int k2 = rest.indexOf('\t');
            String w = (k2 >= 0 ? rest.substring(0, k2) : rest).trim();
            return a + " => " + w;
        }
        k = line.indexOf('\u2192');
        if (k >= 0) return line.substring(0, k).trim() + " => " + line.substring(k + 1).trim();
        k = line.indexOf("->");
        if (k >= 0) return line.substring(0, k).trim() + " => " + line.substring(k + 2).trim();
        k = line.indexOf(" = ");
        if (k >= 0) return line.substring(0, k).trim() + " => " + line.substring(k + 3).trim();
        return null;
    }

    public static Result parse(String text, List<ReplaceRules.Rule> current, int max) {
        Result res = new Result();
        try {
            if (current != null) for (ReplaceRules.Rule r : current) if (r != null && r.kind != ReplaceRules.K_COMMENT) res.rules.add(r);
            if (text == null || text.trim().isEmpty()) { res.empty = true; return res; }
            String[] lines = text.split("\n", -1);
            int read = 0;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].replace("\r", "");
                String t = line.trim();
                if (t.isEmpty() || t.charAt(0) == '#') continue;
                if (++read > MAX_LINES) break;
                if (line.length() > MAX_LINE || t.startsWith("del:") || t.startsWith("del-re:")) { bad(res, i + 1); continue; }
                String norm = normalize(line);
                if (norm == null) { bad(res, i + 1); continue; }
                ReplaceRules.Parsed p = ReplaceRules.parse(norm);
                if (p.rules.size() != 1 || !p.bad.isEmpty()) { bad(res, i + 1); continue; }
                ReplaceRules.Rule r = p.rules.get(0);
                if (r.kind != ReplaceRules.K_TEXT && r.kind != ReplaceRules.K_REGEX) { bad(res, i + 1); continue; }
                if (r.kind == ReplaceRules.K_REGEX) {
                    try { Pattern.compile(r.find); } catch (PatternSyntaxException e) { bad(res, i + 1); continue; }
                }
                int idx = ReplacePairList.indexOf(res.rules, r);
                if (idx >= 0) {
                    ReplaceRules.Rule o = res.rules.get(idx);
                    if (o.with.equals(r.with) && o.word == r.word && o.keep == r.keep && o.smart == r.smart) { res.same++; continue; }
                    res.rules.set(idx, r);
                    res.updated++;
                } else if (res.rules.size() >= max) {
                    res.full++;
                } else {
                    res.rules.add(r);
                    res.added++;
                }
            }
        } catch (Throwable t) { /* what was read so far is returned */ }
        return res;
    }

    private static void bad(Result r, int line) { r.bad++; if (r.firstBad == 0) r.firstBad = line; }
}
