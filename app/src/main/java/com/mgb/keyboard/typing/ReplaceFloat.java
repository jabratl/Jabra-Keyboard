package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.39.0 (FLOAT, step F1) the PURE LOGIC of the floating preview window of Find & Replace (plan: PLAN_RP_FLOAT.txt).
 * PURE JAVA (no Android), never throws, nothing is written anywhere, no second regex run: it only reads the ReplaceEngine.Result
 * that ReplacePanel already computed. The Android part (ReplaceFloatView.kt, later ReplaceFloatWin.kt) only draws what is in here.
 *  - snap(): the data of one window: count text, current n / N, up to 3 rows (current match first, wrap-around), empty message, set lines.
 *  - Row: left context + found + replacement + right context. Before = left + found + right, After = left + repl + right, Changes = found > repl.
 *  - state / tab sanitize (open / mini / closed, tab 0..2), count and bubble text, position math (clamp + fallback), wrap step.
 * Line breaks / tabs become visible marks (same length, so indexes stay valid). Cuts never split a surrogate pair (emoji).
 */
public final class ReplaceFloat {
    private ReplaceFloat() { }

    public static final int OPEN = 0, MINI = 1, CLOSED = 2;
    public static final int TAB_BEFORE = 0, TAB_AFTER = 1, TAB_CHANGES = 2;
    public static final String[] TAB_NAMES = { "Before", "After", "Changes" };
    public static final int CTX = 12;        // context chars each side of the found text
    public static final int MAX_ROWS = 3;
    public static final int MAX_MID = 24;    // found / replacement are cut after this many chars
    public static final String EMPTY = "\u2205";   // shown for an empty found text / empty replacement

    // ------------------------------------------------------------------ state + tab

    /** 0 open, 1 mini (bubble), 2 closed. Anything else = open. */
    public static int sanitizeState(int s) { return s == MINI || s == CLOSED ? s : OPEN; }

    /** 0 Before, 1 After, 2 Changes. Anything else = Before. */
    public static int sanitizeTab(int t) { return t >= 0 && t <= 2 ? t : TAB_BEFORE; }

    // ------------------------------------------------------------------ texts

    /** "4 matches" / "1 match" / "No match" / "Error" / "Type Find"; a set shows "N changes" / "1 change" / "No match". */
    public static String countText(boolean err, boolean blank, int count, boolean setMode) {
        if (err) return "Error";
        if (setMode) return count <= 0 ? "No match" : (count == 1 ? "1 change" : count + " changes");
        if (blank) return "Type Find";
        if (count <= 0) return "No match";
        return count == 1 ? "1 match" : count + " matches";
    }

    /** Text inside the round bubble: the count, "99+" above 99, "!" on an error, "0" when nothing matches. */
    public static String bubbleText(boolean err, int count) {
        if (err) return "!";
        if (count <= 0) return "0";
        return count > 99 ? "99+" : String.valueOf(count);
    }

    /** Next index with wrap-around (n = how many, delta = +1 / -1). n < 1 gives 0. */
    public static int step(int cur, int n, int delta) {
        if (n < 1) return 0;
        int c = cur < 0 ? 0 : (cur >= n ? n - 1 : cur);
        int r = (c + delta) % n;
        return r < 0 ? r + n : r;
    }

    /** Same length, only signs that must not stay: new line / tab / return become visible signs (indexes stay the same). */
    static String flat(String s) {
        if (s == null) return "";
        return s.replace('\n', '\u23CE').replace('\t', '\u21E5').replace('\r', ' ');
    }

    /** Cuts after [max] chars (never inside a surrogate pair) and adds "..." when it cut. Empty text gives the empty sign. */
    static String mid(String s, int max) {
        if (s == null || s.length() == 0) return EMPTY;
        String f = flat(s);
        if (f.length() <= max) return f;
        int e = max;
        if (e > 0 && Character.isHighSurrogate(f.charAt(e - 1))) e--;
        return f.substring(0, e) + "\u2026";
    }

    // ------------------------------------------------------------------ rows

    public static final class Row {
        public final int n;                  // 1-based number of the match
        public final boolean current;
        public final String left, found, repl, right;
        Row(int n, boolean current, String left, String found, String repl, String right) {
            this.n = n; this.current = current; this.left = left; this.found = found; this.repl = repl; this.right = right;
        }
        /** The row as plain text for a tab (tests, accessibility, a fallback when colours cannot be drawn). */
        public String text(int tab) {
            switch (sanitizeTab(tab)) {
                case TAB_AFTER: return left + repl + right;
                case TAB_CHANGES: return found + " \u2192 " + repl + "  #" + n;
                default: return left + found + right;
            }
        }
        /** Start / end of the coloured part inside text(tab). Changes has none (the view colours found and replacement itself). */
        public int[] mark(int tab) {
            switch (sanitizeTab(tab)) {
                case TAB_AFTER: return new int[] { left.length(), left.length() + repl.length() };
                case TAB_CHANGES: return new int[] { 0, 0 };
                default: return new int[] { left.length(), left.length() + found.length() };
            }
        }
    }

    public static final class Snap {
        public String count = "No match";
        public int total = 0;                // matches (or changes of a set)
        public int cur = 0;                  // 1-based current match, 0 = none
        public boolean err = false;
        public boolean setMode = false;
        public String empty = "";            // text for the body when there are no rows
        public final List<Row> rows = new ArrayList<Row>();
        public final List<String> setLines = new ArrayList<String>();
    }

    /** One row of match [i] of [hits]. null when the hit is unusable. Context comes from [src] (clamped). */
    static Row row(String src, ReplaceEngine.Hit h, int i, boolean current) {
        if (h == null) return null;
        String t = src == null ? "" : src;
        int len = t.length();
        int s = Math.max(0, Math.min(h.start, len));
        int e = Math.max(s, Math.min(h.end, len));
        int a = Math.max(0, s - CTX);
        int b = Math.min(len, e + CTX);
        if (a > 0 && a < len && Character.isLowSurrogate(t.charAt(a))) a--;
        if (b < len && b > 0 && Character.isLowSurrogate(t.charAt(b))) b++;
        String left = (a > 0 ? "\u2026" : "") + flat(t.substring(a, s));
        String right = flat(t.substring(e, b)) + (b < len ? "\u2026" : "");
        String f = h.found == null ? "" : h.found;
        String r = h.repl == null ? "" : h.repl;
        return new Row(i + 1, current, left, mid(f, MAX_MID), mid(r, MAX_MID), right);
    }

    /**
     * Everything one window needs. [res] = the result ReplacePanel already has, [cur0] = 0-based current match (step), [setLines] = lines of an active
     * rule set (null when no set). Rows: current match first, then the next ones (wrap-around), max MAX_ROWS. Never null, never throws.
     */
    public static Snap snap(String src, ReplaceEngine.Result res, int cur0, boolean setMode, List<String> setLines) {
        Snap p = new Snap();
        try {
            p.setMode = setMode;
            if (res == null) { p.empty = "No result"; p.count = countText(false, false, 0, setMode); return p; }
            p.err = res.err != null;
            p.total = res.err != null ? 0 : res.count;
            if (setMode) {
                p.total = res.changed;
                p.count = countText(p.err, false, p.total, true);
                if (setLines != null) for (String l : setLines) { if (p.setLines.size() >= 6) break; if (l != null) p.setLines.add(l); }
                if (p.setLines.isEmpty()) p.empty = p.total == 0 ? "No match" : "";
                return p;
            }
            p.count = countText(p.err, res.blank, p.total, false);
            if (p.err) { p.empty = res.err == null ? "Error" : res.err; return p; }
            if (res.blank) { p.empty = "Type what to find"; return p; }
            int n = res.hits == null ? 0 : res.hits.size();
            if (res.count <= 0 || n == 0) { p.empty = "No match"; return p; }
            int c = cur0 < 0 ? 0 : (cur0 >= n ? n - 1 : cur0);
            p.cur = c + 1;
            int show = Math.min(MAX_ROWS, n);
            for (int k = 0; k < show; k++) {
                int i = (c + k) % n;
                Row r = row(src, res.hits.get(i), i, k == 0);
                if (r != null) p.rows.add(r);
            }
            if (p.rows.isEmpty()) p.empty = "No match";
            return p;
        } catch (Throwable e) {
            p.rows.clear(); p.setLines.clear(); p.empty = "Preview not available";
            return p;
        }
    }

    // ------------------------------------------------------------------ position math

    /**
     * Keeps a window / bubble (w x h) fully inside the bounds (left, top, right, bottom) with [margin] to the edge.
     * Bigger than the room = it sticks to the left / top edge. Returns {x, y}. Never throws.
     */
    public static int[] clamp(int x, int y, int w, int h, int left, int top, int right, int bottom, int margin) {
        int m = Math.max(0, margin);
        int minX = left + m, minY = top + m;
        int maxX = right - m - Math.max(0, w), maxY = bottom - m - Math.max(0, h);
        int cx = maxX < minX ? minX : Math.max(minX, Math.min(x, maxX));
        int cy = maxY < minY ? minY : Math.max(minY, Math.min(y, maxY));
        return new int[] { cx, cy };
    }

    /**
     * Where to put the window: the saved place (savedX / savedY, -1 = none) kept inside the bounds, or the fallback (centred, at the top) when nothing
     * is saved or the saved place is clearly outside (rotation, another phone). "Clearly outside" = the window would have less than half inside.
     */
    public static int[] place(int savedX, int savedY, int w, int h, int left, int top, int right, int bottom, int margin) {
        boolean none = savedX < 0 || savedY < 0;
        boolean outside = !none && (savedX + w / 2 > right || savedY + h / 2 > bottom || savedX + w / 2 < left || savedY + h / 2 < top);
        if (none || outside) {
            int x = left + (right - left - w) / 2;
            return clamp(x, top + Math.max(0, margin), w, h, left, top, right, bottom, margin);
        }
        return clamp(savedX, savedY, w, h, left, top, right, bottom, margin);
    }
}
