package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.32.0 (PEEK) the model of the long-press card of Find & Replace: Before / After rows (the changed part marked) + rule counts.
 * PURE JAVA (no Android), never throws, nothing is written anywhere. The Android part (PeekPopup.kt) only draws what is in here.
 *  - forResult(): the first matches of [Replace all] (uses Result.hits + ReplaceEngine.lineParts).
 *  - forSet(): what a rule set would change on the text: first changed lines (common start / end cut away, the middle marked) + the rule list with counts (ReplaceExtras.setPreview).
 * Marks are INDEX ranges {start, end} in the row text (never visual positions, so RTL / Arabic lines work the same). Windows never cut a surrogate pair.
 */
public final class ReplacePeek {
    private ReplacePeek() { }

    public static final int LIMIT = 600;      // card text budget (chars)
    public static final int WIN = 80;         // a line longer than this is shown as a window round the changed part
    public static final int CTX = 24;         // context chars left / right of the changed part in a window
    public static final int MAX_MID = 40;     // the changed part itself is cut after this many chars
    public static final int SET_ROWS = 3;

    public static final class Row {
        public final String before, after;
        public final int[] bm, am;            // {start, end} of the changed part in before / after (start == end = nothing to colour)
        Row(String before, int[] bm, String after, int[] am) { this.before = before; this.bm = bm; this.after = after; this.am = am; }
    }

    public static final class Peek {
        public String title = "";
        public String summary = "";
        public String more = "";
        public String hint = "";
        public int total = 0;                 // all matches / changed lines (rows may show fewer)
        public String unit = "matches";
        public final List<Row> rows = new ArrayList<Row>();
        public final List<String> rules = new ArrayList<String>();

        void fixMore() { more = total > rows.size() ? "+" + (total - rows.size()) + " more " + unit : ""; }

        public int size() {
            int n = title.length() + summary.length() + more.length();
            for (Row r : rows) n += r.before.length() + r.after.length();
            for (String s : rules) n += s.length();
            return n;
        }

        /** The whole card as plain text (tests, and a fallback when colours cannot be drawn). */
        public String text() {
            StringBuilder b = new StringBuilder();
            if (!title.isEmpty()) b.append(title);
            if (!summary.isEmpty()) { if (b.length() > 0) b.append('\n'); b.append(summary); }
            for (Row r : rows) b.append("\n- ").append(r.before).append("\n> ").append(r.after);
            if (!more.isEmpty()) b.append('\n').append(more);
            for (String s : rules) b.append('\n').append(s);
            return b.toString();
        }
    }

    /** A card with only a title and one line (e.g. "Not available in password fields."). Never null. */
    public static Peek message(String title, String text) {
        Peek p = new Peek();
        p.title = title == null ? "" : ReplaceExtras.head(title, 30);
        p.summary = text == null ? "" : text;
        p.hint = "Release to close";
        return p;
    }

    private static Peek fail(Peek p, String why) { p.rows.clear(); p.rules.clear(); p.more = ""; p.summary = why; return p; }

    /** Keeps the card small: drops rules (down to 3), then rows (down to 1) while the text is above LIMIT. */
    private static void fit(Peek p) {
        try {
            while (p.size() > LIMIT && p.rules.size() > 3) p.rules.remove(p.rules.size() - 1);
            while (p.size() > LIMIT && p.rows.size() > 1) { p.rows.remove(p.rows.size() - 1); p.fixMore(); }
            p.fixMore();
        } catch (Throwable e) { /* keep as is */ }
    }

    // ------------------------------------------------------------------ [Replace all]

    /** First [maxRows] matches of a Replace all: Before = context + found (marked), After = context + replacement (marked). ctx = context chars each side. */
    public static Peek forResult(String text, ReplaceEngine.Result res, int maxRows, int ctx) {
        Peek p = new Peek();
        p.title = "Replace all";
        p.hint = "Release to close";
        try {
            String t = text == null ? "" : text;
            if (res == null) return fail(p, "No result");
            if (t.length() > ReplaceEngine.MAX_TEXT) return fail(p, "Text is too long for a peek (over " + ReplaceEngine.MAX_TEXT + " chars)");
            if (t.isEmpty() && res.err == null) return fail(p, "No text: select text or type some first");
            p.summary = ReplaceEngine.info(res);
            if (res.err != null || res.blank || res.count <= 0 || res.hits == null || res.hits.isEmpty()) return fail(p, p.summary);
            int rowsMax = maxRows < 1 ? 1 : (maxRows > 5 ? 5 : maxRows);
            int c = ctx < 0 ? 0 : (ctx > 60 ? 60 : ctx);
            int n = Math.min(rowsMax, res.hits.size());
            for (int i = 0; i < n; i++) {
                String[] q = ReplaceEngine.lineParts(t, res.hits.get(i), c);
                int s = q[0].length();
                p.rows.add(new Row(q[0] + q[1] + q[3], new int[] { s, s + q[1].length() }, q[0] + q[2] + q[3], new int[] { s, s + q[2].length() }));
            }
            p.total = res.count;
            p.unit = "matches";
            fit(p);
            return p;
        } catch (Throwable e) { return fail(p, "Peek not available"); }
    }

    // ------------------------------------------------------------------ a set

    /** What the set [rulesText] would do on [text]: name, summary, first changed lines (max 3) + the first rules with counts. Nothing is changed. */
    public static Peek forSet(String name, String rulesText, ReplaceEngine.Opt o, String text) {
        Peek p = new Peek();
        p.hint = "Release to close, tap to load";
        p.unit = "lines";
        String n = name == null || name.trim().isEmpty() ? "(no name)" : name.trim();
        p.title = ReplaceExtras.head(n, 30);
        try {
            String t = text == null ? "" : text;
            if (t.length() > ReplaceEngine.MAX_TEXT) return fail(p, "Text is too long for a peek (over " + ReplaceEngine.MAX_TEXT + " chars)");
            String sp = ReplaceExtras.setPreview(name, rulesText, o, t);
            if (sp.isEmpty()) return fail(p, "Peek not available");
            String[] ls = sp.split("\n", -1);
            String first = ls[0];
            if (first.startsWith(n + ": ")) first = first.substring(n.length() + 2);
            p.summary = first;
            for (int i = 1; i < ls.length; i++) if (!ls[i].isEmpty()) p.rules.add(ls[i]);
            if (!t.isEmpty()) {
                ReplaceRules.Parsed pr = ReplaceRules.parse(rulesText);
                if (pr.active() > 0) {
                    ReplaceRules.RunResult rr = ReplaceRules.run(t, pr.rules, o);
                    if (rr.out != null && !rr.out.equals(t)) addRows(p, t, rr.out);
                }
            }
            fit(p);
            return p;
        } catch (Throwable e) { return fail(p, "Peek not available"); }
    }

    private static void addRows(Peek p, String a, String b) {
        String[] la = a.split("\n", -1), lb = b.split("\n", -1);
        int total = 0;
        if (la.length == lb.length) {
            for (int i = 0; i < la.length; i++) {
                if (la[i].equals(lb[i])) continue;
                total++;
                if (p.rows.size() < SET_ROWS) p.rows.add(rowOf(la[i], lb[i]));
            }
        } else {   // lines were removed / added (del: rules): one row over the whole text
            total = 1;
            p.rows.add(rowOf(a, b));
        }
        p.total = total;
        p.fixMore();
    }

    // ------------------------------------------------------------------ diff + window helpers (public for the test)

    /** Same length, only symbols that must not stay: new line / tab / return become visible signs (indexes stay the same). */
    static String flat(String s) {
        if (s == null) return "";
        return s.replace('\n', '\u23CE').replace('\t', '\u21E5').replace('\r', ' ');
    }

    /** {common start length, common end length}; never overlapping, never splitting a surrogate pair. null = {0, 0}. */
    public static int[] diffMiddle(String a, String b) {
        if (a == null || b == null) return new int[] { 0, 0 };
        int n = Math.min(a.length(), b.length());
        int p = 0;
        while (p < n && a.charAt(p) == b.charAt(p)) p++;
        if (p > 0 && Character.isHighSurrogate(a.charAt(p - 1))) p--;
        int s = 0;
        while (s < n - p && a.charAt(a.length() - 1 - s) == b.charAt(b.length() - 1 - s)) s++;
        if (s > 0 && Character.isLowSurrogate(a.charAt(a.length() - s))) s--;
        return new int[] { p, s };
    }

    private static final class Win {
        final String text; final int[] m;
        Win(String text, int[] m) { this.text = text; this.m = m; }
    }

    private static Win window(String s, int ms, int me, boolean force) {
        int len = s.length();
        ms = Math.max(0, Math.min(ms, len));
        me = Math.max(ms, Math.min(me, len));
        if (!force) return new Win(s, new int[] { ms, me });
        int a = Math.max(0, ms - CTX);
        int b = Math.min(len, me + CTX);
        String mid = s.substring(ms, me);
        boolean cut = false;
        if (mid.length() > MAX_MID) { mid = ReplaceExtras.head(mid, MAX_MID); cut = true; }
        if (a > 0 && a < len && Character.isLowSurrogate(s.charAt(a))) a--;
        if (b < len && b > 0 && Character.isLowSurrogate(s.charAt(b))) b++;
        StringBuilder sb = new StringBuilder();
        if (a > 0) sb.append('\u2026');
        sb.append(s, a, ms);
        int st = sb.length();
        sb.append(mid);
        int en = sb.length();
        if (cut) sb.append('\u2026');
        else { sb.append(s, me, b); if (b < len) sb.append('\u2026'); }
        return new Win(sb.toString(), new int[] { st, en });
    }

    /** One Before / After row of two texts (new lines / tabs made visible, long ones shown as a window round the changed part). */
    static Row rowOf(String a0, String b0) {
        String a = flat(a0), b = flat(b0);
        int[] d = diffMiddle(a, b);
        boolean force = a.length() > WIN || b.length() > WIN;
        Win wa = window(a, d[0], a.length() - d[1], force);
        Win wb = window(b, d[0], b.length() - d[1], force);
        return new Row(wa.text, wa.m, wb.text, wb.m);
    }
}
