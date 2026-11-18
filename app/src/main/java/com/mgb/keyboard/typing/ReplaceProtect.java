package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.41.0 NEVER-CHANGE LIST (idea from the Bulk Replace tool "Do-not-translate", item 6). PURE JAVA (no Android), never throws.
 * A list of terms (names, titles ...). While a Replace runs (single Find / With, a pair list or a set) a match that overlaps one of these terms
 * in the text is SKIPPED, even when the match covers only a part of the term: with "Mark Twain" on the list, the rule "Mark => Luke" leaves
 * "Mark Twain" alone and still changes the other "Mark". Match case follows the run (Aa switch / set flag).
 * The list is plain text, one term per line (pref rp_protect); limits: MAX_TERMS terms, MAX_LEN characters each; no empty lines, no repeats (any case).
 * ranges() finds the protected places in a text, overlaps() tests one match against them. ReplaceEngine.run does the skipping (Opt.protect).
 */
public final class ReplaceProtect {
    private ReplaceProtect() { }

    public static final int MAX_TERMS = 100;
    public static final int MAX_LEN = 60;
    public static final int MAX_RANGES = 5000;

    public static final int ADDED = 0, DUPLICATE = 1, FULL = 2, EMPTY = 3;

    private static boolean same(String a, String b) { return a.equalsIgnoreCase(b); }

    /** One cleaned term: trimmed, line breaks become a space, cut to MAX_LEN (not inside a surrogate pair). "" = nothing. */
    public static String clean(String s) {
        if (s == null) return "";
        String t = s.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (t.length() > MAX_LEN) {
            int e = MAX_LEN;
            if (Character.isHighSurrogate(t.charAt(e - 1))) e--;
            t = t.substring(0, e).trim();
        }
        return t;
    }

    /** The list from stored text (one term per line; empty lines, lines starting with # and repeats are dropped). */
    public static List<String> parse(String text) {
        List<String> out = new ArrayList<String>();
        try {
            if (text == null) return out;
            for (String line : text.split("\n", -1)) {
                String t = clean(line);
                if (t.isEmpty() || t.charAt(0) == '#' || contains(out, t)) continue;
                if (out.size() >= MAX_TERMS) break;
                out.add(t);
            }
        } catch (RuntimeException e) { /* what was read so far stays */ }
        return out;
    }

    public static String text(List<String> list) {
        StringBuilder sb = new StringBuilder();
        if (list != null) for (String t : list) { if (sb.length() > 0) sb.append('\n'); sb.append(t); }
        return sb.toString();
    }

    public static boolean contains(List<String> list, String term) {
        if (list == null || term == null) return false;
        String t = term.trim();
        for (String x : list) if (x != null && same(x, t)) return true;
        return false;
    }

    /** Adds one term (the list is changed). Returns ADDED / DUPLICATE / FULL / EMPTY. */
    public static int add(List<String> list, String term) {
        String t = clean(term);
        if (t.isEmpty() || t.charAt(0) == '#') return EMPTY;
        if (contains(list, t)) return DUPLICATE;
        if (list.size() >= MAX_TERMS) return FULL;
        list.add(t);
        return ADDED;
    }

    /** Adds many lines (a pasted list). Returns {added, duplicates, notAdded because the list is full}. */
    public static int[] addAll(List<String> list, String lines) {
        int[] n = new int[3];
        try {
            if (lines == null) return n;
            int seen = 0;
            for (String line : lines.split("\n", -1)) {
                if (++seen > 1000) break;
                int r = add(list, line);
                if (r == ADDED) n[0]++; else if (r == DUPLICATE) n[1]++; else if (r == FULL) n[2]++;
            }
        } catch (RuntimeException e) { /* counts so far */ }
        return n;
    }

    /** The places of the text that must not change: {start, end} pairs, ascending, not overlapping (longest term first when two start together). At most MAX_RANGES. Never throws. */
    public static List<int[]> ranges(String text, List<String> terms, boolean matchCase) {
        List<int[]> out = new ArrayList<int[]>();
        try {
            if (text == null || text.isEmpty() || terms == null || terms.isEmpty()) return out;
            List<String> ts = new ArrayList<String>();
            for (String x : terms) if (x != null && !x.isEmpty()) ts.add(x);
            if (ts.isEmpty()) return out;
            Collections.sort(ts, new Comparator<String>() { public int compare(String a, String b) { return b.length() - a.length(); } });
            StringBuilder sb = new StringBuilder();
            for (String x : ts) { if (sb.length() > 0) sb.append('|'); sb.append(Pattern.quote(x)); }
            Pattern p = Pattern.compile(sb.toString(), matchCase ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            Matcher m = p.matcher(text);
            while (m.find() && out.size() < MAX_RANGES) {
                if (m.end() > m.start()) out.add(new int[] { m.start(), m.end() });
            }
        } catch (RuntimeException e) { /* a bad list must never stop a replace */ }
        return out;
    }

    /** Does the match [a, b) touch a protected place? */
    public static boolean overlaps(List<int[]> ranges, int a, int b) {
        if (ranges == null) return false;
        for (int[] r : ranges) { if (a < r[1] && r[0] < b) return true; if (r[0] >= b) break; }
        return false;
    }

    /** "Never change (3)" / "Never change (none)". */
    public static String label(int n) { return n <= 0 ? "Never change (none)" : "Never change (" + n + ")"; }
}
