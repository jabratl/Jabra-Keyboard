package com.mgb.keyboard.formatter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.52.0 (formatter redesign): pure Java helpers for the one-screen Formatter. No Android, no engine change.
 *  - changes(): "what will change" counts per kind (quotes, dots, dashes, ...) read from the SOURCE text + options, for the summary chips.
 *  - spans(): where those spots are in the source (Original view highlights the tapped chip).
 *  - rule lines: one line = one rule; a rule is switched OFF by the prefix "#off " (the engine already treats # lines as comments, so OFF = no effect).
 * The counts are a guide (the engine stays the only thing that makes the real result).
 */
public final class FmtChanges {
    private FmtChanges() { }

    public static final String OFF = "#off ";
    public static final String[] KEYS = { "z", "m", "d", "a", "q", "b", "s", "t", "r" };
    public static final String[] NAMES = { "invisible", "markdown", "dots", "dashes", "quotes", "blank lines", "scene break", "title", "rules" };

    public static String name(String k) { for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(k)) return NAMES[i]; return k; }

    private static boolean on(Map<String, String> o, String k) { return "true".equals(o.get(k)); }
    private static String get(Map<String, String> o, String k) { String v = o.get(k); return v == null ? "" : v; }

    private static final Pattern ZW = Pattern.compile("[\u200b\ufeff]");
    private static final Pattern MD = Pattern.compile("\\*\\*[^*\\n]+\\*\\*|(?<![*])\\*[^*\\n]+\\*(?![*])|^#{1,3}\\s+", Pattern.MULTILINE);
    private static final Pattern DOTS = Pattern.compile("\\.{3,}|\\.(?: \\.){2,}");
    private static final Pattern DASH = Pattern.compile(" ?-- ?");
    private static final Pattern BLANKS = Pattern.compile("\\n[ \\t]*\\n(?:[ \\t]*\\n)+");
    private static final Pattern SCENE = Pattern.compile("^[ \\t]*(?:[*\\-\\u2013\\u2014_=~#\\u2022][ \\t]*){3,}$", Pattern.MULTILINE);

    /** One spot in the source text: [start, end) of kind k. */
    public static final class Span {
        public final int start, end; public final String k;
        Span(int s, int e, String k) { start = s; end = e; this.k = k; }
    }

    private static void add(List<Span> out, Pattern p, String text, String k) {
        Matcher m = p.matcher(text);
        while (m.find()) if (m.end() > m.start()) out.add(new Span(m.start(), m.end(), k));
    }

    /** All spots the current options would touch, sorted by position. */
    public static List<Span> spans(String text, Map<String, String> o) {
        List<Span> out = new ArrayList<Span>();
        if (text == null || text.isEmpty()) return out;
        if (on(o, "zw")) add(out, ZW, text, "z");
        if (on(o, "md")) add(out, MD, text, "m");
        if (on(o, "dots")) add(out, DOTS, text, "d");
        if (on(o, "dash")) add(out, DASH, text, "a");
        String q = get(o, "quotes");
        if (q.equals("curly")) add(out, Pattern.compile("[\"']"), text, "q");
        else if (q.equals("straight")) add(out, Pattern.compile("[\u201c\u201d\u2018\u2019]"), text, "q");
        if (on(o, "collapse")) add(out, BLANKS, text, "b");
        if (!get(o, "scene").equals("text")) add(out, SCENE, text, "s");
        String ah = get(o, "autoH");
        if (!ah.isEmpty()) {
            String[] ls = text.split("\n", -1); int pos = 0, nth = 0;
            for (String l : ls) {
                String tr = l.trim();
                if (!tr.isEmpty()) {
                    nth++;
                    if (nth > 3) break;
                    if (tr.length() <= 120 && NovelFormatter.HRE.matcher(tr).find() && !SCENE.matcher(tr).matches()) { out.add(new Span(pos, pos + l.length(), "t")); break; }
                }
                pos += l.length() + 1;
            }
        }
        for (Span s : ruleSpans(text, get(o, "rules"))) out.add(s);
        java.util.Collections.sort(out, new java.util.Comparator<Span>() { public int compare(Span a, Span b) { return a.start != b.start ? (a.start < b.start ? -1 : 1) : (a.end < b.end ? -1 : (a.end == b.end ? 0 : 1)); } });
        return out;
    }

    /** kind -> count (insertion order = KEYS order), only kinds with at least one spot. */
    public static Map<String, Integer> changes(String text, Map<String, String> o) {
        Map<String, Integer> c = new LinkedHashMap<String, Integer>();
        List<Span> sp = spans(text, o);
        for (String k : KEYS) { int n = 0; for (Span s : sp) if (s.k.equals(k)) n++; if (n > 0) c.put(k, n); }
        return c;
    }

    public static int total(Map<String, Integer> c) { int t = 0; for (Integer v : c.values()) t += v; return t; }

    /** True when the first non-empty line looks like a chapter title and could become a heading (for the suggestion chip). */
    public static boolean titleSuggest(String text) {
        if (text == null) return false;
        for (String l : text.split("\n", -1)) {
            String tr = l.trim();
            if (tr.isEmpty()) continue;
            return tr.length() <= 120 && NovelFormatter.HRE.matcher(tr).find() && !SCENE.matcher(tr).matches();
        }
        return false;
    }

    // ---------- rules ----------
    /** One parsed rule line. line = index in the rules text, off = starts with "#off ", text = the rule without the OFF prefix, valid = a rule (not a plain comment / blank). */
    public static final class RuleLine {
        public final int line; public final boolean off, valid; public final String text;
        RuleLine(int line, boolean off, boolean valid, String text) { this.line = line; this.off = off; this.valid = valid; this.text = text; }
        public String find() { return part(text, true); }
        public String to() { return part(text, false); }
    }

    private static String part(String t, boolean find) {
        String s = t.trim();
        if (s.startsWith("del-re:")) return find ? s : "";
        if (s.startsWith("del:")) return find ? s : "";
        int k = s.indexOf("=>");
        if (k < 0) return find ? s : "";
        return find ? s.substring(0, k).trim() : s.substring(k + 2).trim();
    }

    public static List<RuleLine> ruleLines(String rules) {
        List<RuleLine> out = new ArrayList<RuleLine>();
        String[] ls = (rules == null ? "" : rules).split("\n", -1);
        for (int i = 0; i < ls.length; i++) {
            String l = ls[i].replace("\r", "");
            String tl = l.trim();
            if (tl.isEmpty()) continue;
            if (tl.startsWith(OFF.trim() + " ") || tl.equals(OFF.trim())) {
                String rest = tl.substring(OFF.trim().length()).trim();
                if (rest.isEmpty() || rest.startsWith("#")) continue;
                out.add(new RuleLine(i, true, true, rest));
            } else if (tl.charAt(0) == '#') continue;
            else out.add(new RuleLine(i, false, true, tl));
        }
        return out;
    }

    /** Flip one rule line on <-> off. Other lines are kept byte for byte. */
    public static String toggleRule(String rules, int line) {
        String[] ls = (rules == null ? "" : rules).split("\n", -1);
        if (line < 0 || line >= ls.length) return rules;
        String l = ls[line], tl = l.trim();
        if (tl.startsWith(OFF.trim() + " ")) ls[line] = tl.substring(OFF.trim().length()).trim();
        else ls[line] = OFF + tl;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ls.length; i++) { if (i > 0) sb.append('\n'); sb.append(ls[i]); }
        return sb.toString();
    }

    private static Pattern patternOf(String find) {
        try {
            String f = find.trim();
            if (f.startsWith("re:")) return Pattern.compile(f.substring(3).trim());
            if (f.startsWith("del-re:")) return Pattern.compile(f.substring(7).trim());
            if (f.startsWith("del:")) return Pattern.compile(Pattern.quote(f.substring(4).trim()));
            if (f.isEmpty()) return null;
            return Pattern.compile(Pattern.quote(f));
        } catch (Exception e) { return null; }
    }

    /** How many times this rule matches in text (0 for an unreadable rule). */
    public static int matches(String text, RuleLine r) {
        if (text == null || !r.valid) return 0;
        Pattern p = patternOf(r.find());
        if (p == null) return 0;
        int n = 0; Matcher m = p.matcher(text);
        while (m.find() && n < 9999) { n++; if (m.end() == m.start() && m.end() >= text.length()) break; }
        return n;
    }

    static List<Span> ruleSpans(String text, String rules) {
        List<Span> out = new ArrayList<Span>();
        for (RuleLine r : ruleLines(rules)) {
            if (r.off) continue;
            Pattern p = patternOf(r.find());
            if (p == null) continue;
            Matcher m = p.matcher(text); int n = 0;
            while (m.find() && n++ < 500) if (m.end() > m.start()) out.add(new Span(m.start(), m.end(), "r"));
        }
        return out;
    }

    // ---------- section summaries ----------
    public static String paraSummary(Map<String, String> o) {
        String al = get(o, "align");
        return (on(o, "indent") ? get(o, "indentVal") + get(o, "indentUnit") : "no indent") + " \u00B7 gap " + get(o, "spacing") + " \u00B7 " + (al.equals("none") || al.isEmpty() ? "auto" : al);
    }

    public static String keepSummary(Map<String, String> o) {
        StringBuilder sb = new StringBuilder();
        if (on(o, "keepB")) sb.append("B ");
        if (on(o, "keepI")) sb.append("I ");
        if (on(o, "keepU")) sb.append("U ");
        String s = sb.toString().trim();
        return s.isEmpty() ? "none" : s;
    }

    public static String cleanSummary(Map<String, String> o) {
        int n = 0;
        String[] ks = { "collapse", "md", "zw", "dots", "dash" };
        for (String k : ks) if (on(o, k)) n++;
        if (!get(o, "quotes").equals("none") && !get(o, "quotes").isEmpty()) n++;
        if (!get(o, "autoH").isEmpty()) n++;
        return n + " on";
    }

    public static String rulesSummary(Map<String, String> o) {
        int on = 0, all = 0;
        for (RuleLine r : ruleLines(get(o, "rules"))) { all++; if (!r.off) on++; }
        return on + " of " + all + " on";
    }
}
