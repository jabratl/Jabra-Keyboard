package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * v0.48.1 Formatter "Clip: Latest - tap to choose another" smart list. Pure Java (no Android); tested by tools/ClipPickPlanTest.java.
 * Takes the clipboard history (newest first) and returns it ready to show: same text only once, an optional filter
 * (All / Long / Short / Bangla / HTML), a sort (Newest / Longest), and for every clip a title line + a small info line
 * (chars, words, paragraphs, tag). Nothing is changed or stored; the caller keeps the original index to know which clip was tapped.
 */
public final class ClipPickPlan {
    private ClipPickPlan() { }

    public static final int F_ALL = 0, F_LONG = 1, F_SHORT = 2, F_BANGLA = 3, F_HTML = 4;
    public static final String[] F_NAMES = { "All", "Long", "Short", "Bangla", "HTML" };
    public static final int S_NEWEST = 0, S_LONGEST = 1;
    public static final String[] S_NAMES = { "Newest", "Longest" };

    /** A clip this long (characters) counts as a chapter-size text. */
    public static final int LONG_MIN = 300;
    public static final int TITLE_MAX = 54;
    public static final int TAIL_MAX = 16;
    public static final int MAX_SHOWN = 40;

    public static final class Entry {
        public final int index;        // position in the list given to plan() (0 = newest = "Latest")
        public final String text;
        public final boolean html;
        public final int chars, words, paras;
        public final boolean bangla;
        public final String title;     // first useful line, short
        public final String tail;      // end of the text, short ("" when the clip is short)
        Entry(int index, String text, boolean html, int chars, int words, int paras, boolean bangla, String title, String tail) {
            this.index = index; this.text = text; this.html = html; this.chars = chars; this.words = words; this.paras = paras;
            this.bangla = bangla; this.title = title; this.tail = tail;
        }

        /** Second line of the row: "2,340 chars - 410 words - 12 para - Bangla - HTML - Latest". */
        public String info() {
            StringBuilder b = new StringBuilder();
            b.append(group(chars)).append(" chars");
            if (words > 0) b.append(" \u00B7 ").append(group(words)).append(words == 1 ? " word" : " words");
            if (paras > 1) b.append(" \u00B7 ").append(paras).append(" para");
            if (bangla) b.append(" \u00B7 Bangla");
            if (html) b.append(" \u00B7 HTML");
            if (index == 0) b.append(" \u00B7 Latest");
            return b.toString();
        }

        /** First line of the row: the title, and (long clips) the end of the text so two chapters that start alike can be told apart. */
        public String head() {
            if (tail.isEmpty()) return title;
            return title + "  \u2026  " + tail;
        }
    }

    public static int sanitizeFilter(int f) { return f < 0 || f >= F_NAMES.length ? F_ALL : f; }
    public static int sanitizeSort(int s) { return s < 0 || s >= S_NAMES.length ? S_NEWEST : s; }
    public static int nextFilter(int f) { return (sanitizeFilter(f) + 1) % F_NAMES.length; }
    public static int nextSort(int s) { return (sanitizeSort(s) + 1) % S_NAMES.length; }

    /** 12345 -> "12,345". */
    static String group(int n) {
        String s = Integer.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(',');
            b.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + b;
    }

    static int words(String t) {
        int n = 0;
        boolean in = false;
        for (int i = 0; i < t.length(); i++) {
            boolean sp = Character.isWhitespace(t.charAt(i));
            if (!sp && !in) n++;
            in = !sp;
        }
        return n;
    }

    /** Paragraphs = blocks of non-empty lines (a blank line or a single line break between lines both start a new paragraph for novel text, so every non-empty line counts). */
    static int paras(String t) {
        int n = 0;
        String[] ls = t.split("\\r?\\n");
        for (String l : ls) if (!l.trim().isEmpty()) n++;
        return n;
    }

    static boolean isBangla(String t) {
        int letters = 0, bn = 0;
        int lim = Math.min(t.length(), 2000);
        for (int i = 0; i < lim; i++) {
            char c = t.charAt(i);
            if (c >= 0x0980 && c <= 0x09FF) { if (!(c >= 0x09E6 && c <= 0x09EF)) { bn++; letters++; } }
            else if (Character.isLetter(c)) letters++;
        }
        return bn >= 3 && bn * 2 >= letters;
    }

    static String oneLine(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max).trim() + "\u2026" : s;
    }

    /** First non-empty line, cut short. */
    static String titleOf(String t) {
        for (String l : t.split("\\r?\\n")) {
            String x = oneLine(l);
            if (!x.isEmpty()) return cut(x, TITLE_MAX);
        }
        return "";
    }

    /** End of a long clip (last non-empty line, last TAIL_MAX chars). "" for a short clip or when it would repeat the title. */
    static String tailOf(String t, String title) {
        if (t.length() < LONG_MIN) return "";
        String last = "";
        String[] ls = t.split("\\r?\\n");
        for (int i = ls.length - 1; i >= 0; i--) {
            String x = oneLine(ls[i]);
            if (!x.isEmpty()) { last = x; break; }
        }
        if (last.isEmpty()) return "";
        String tl = last.length() > TAIL_MAX ? last.substring(last.length() - TAIL_MAX).trim() : last;
        if (title.endsWith(tl) || title.equals(last)) return "";
        return tl;
    }

    static boolean passes(Entry e, int filter) {
        switch (filter) {
            case F_LONG: return e.chars >= LONG_MIN;
            case F_SHORT: return e.chars < LONG_MIN;
            case F_BANGLA: return e.bangla;
            case F_HTML: return e.html;
            default: return true;
        }
    }

    /**
     * texts = clipboard history newest first; hasHtml = same length (null = no clip has stored HTML).
     * Empty / blank clips are skipped; the same text appears once (the newest one wins).
     */
    public static List<Entry> plan(List<String> texts, List<Boolean> hasHtml, int filter, int sort) {
        int f = sanitizeFilter(filter), s = sanitizeSort(sort);
        ArrayList<Entry> all = new ArrayList<Entry>();
        if (texts == null) return all;
        HashSet<String> seen = new HashSet<String>();
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t == null || t.trim().isEmpty()) continue;
            if (!seen.add(t)) continue;
            boolean h = hasHtml != null && i < hasHtml.size() && hasHtml.get(i) != null && hasHtml.get(i);
            String title = titleOf(t);
            all.add(new Entry(i, t, h, t.length(), words(t), paras(t), isBangla(t), title, tailOf(t, title)));
        }
        ArrayList<Entry> out = new ArrayList<Entry>();
        for (Entry e : all) if (passes(e, f)) out.add(e);
        if (s == S_LONGEST) {
            // stable insertion sort by chars, longest first (ties keep newest first)
            for (int i = 1; i < out.size(); i++) {
                Entry x = out.get(i);
                int j = i - 1;
                while (j >= 0 && out.get(j).chars < x.chars) { out.set(j + 1, out.get(j)); j--; }
                out.set(j + 1, x);
            }
        }
        if (out.size() > MAX_SHOWN) return new ArrayList<Entry>(out.subList(0, MAX_SHOWN));
        return out;
    }

    /** How many clips each filter would show (for the chip text "Long (3)"). */
    public static int count(List<String> texts, List<Boolean> hasHtml, int filter) {
        int f = sanitizeFilter(filter);
        if (texts == null) return 0;
        HashSet<String> seen = new HashSet<String>();
        int n = 0;
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t == null || t.trim().isEmpty() || !seen.add(t)) continue;
            boolean h = hasHtml != null && i < hasHtml.size() && hasHtml.get(i) != null && hasHtml.get(i);
            String title = titleOf(t);
            if (passes(new Entry(i, t, h, t.length(), 0, 0, isBangla(t), title, ""), f)) n++;
        }
        return n;
    }
}
