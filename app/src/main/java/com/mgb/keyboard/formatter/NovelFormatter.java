package com.mgb.keyboard.formatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.0.8 Novel formatter engine. Pure Java (no Android classes) so it can be tested on a computer.
 * It is a line-by-line port of novel-formatter_v2_updated.html (same option keys, same rule syntax,
 * same output). Option keys = the HTML "data-k" names, so presets JSON stays compatible
 * (nvPresets / nvCurrent): indent indentVal indentUnit skipFirst spacing spacingUnit lh fs align
 * keepB keepI keepU headings hcenter scene sceneText collapse quotes dots dash md zw autoH rules mode sep.
 * Booleans are the strings "true"/"false".
 */
public final class NovelFormatter {

    private NovelFormatter() { }

    // ---------- data ----------
    public static final class Run {
        public String t; public boolean b, i, u;
        public Run(String t, boolean b, boolean i, boolean u) { this.t = t; this.b = b; this.i = i; this.u = u; }
    }

    public static final class Block {
        public final List<Run> runs; public final int h;
        public Block(List<Run> runs, int h) { this.runs = runs; this.h = h; }
    }

    public static final class Result {
        public final String out; public final int paras, words, bad;
        public Result(String out, int paras, int words, int bad) { this.out = out; this.paras = paras; this.words = words; this.bad = bad; }
    }

    /** Defaults = what the HTML page shows when it is first opened. */
    public static Map<String, String> defaults() {
        Map<String, String> d = new LinkedHashMap<String, String>();
        d.put("indent", "true"); d.put("indentVal", "2"); d.put("indentUnit", "em"); d.put("skipFirst", "true");
        d.put("spacing", "1"); d.put("spacingUnit", "em"); d.put("lh", ""); d.put("fs", ""); d.put("align", "none");
        d.put("keepB", "true"); d.put("keepI", "true"); d.put("keepU", "true"); d.put("headings", "true"); d.put("hcenter", "false");
        d.put("scene", "center"); d.put("sceneText", "* * *");
        d.put("collapse", "true"); d.put("quotes", "none"); d.put("dots", "false"); d.put("dash", "false");
        d.put("md", "true"); d.put("zw", "true"); d.put("autoH", "");
        d.put("rules", ""); d.put("mode", "inline"); d.put("sep", "single");
        return d;
    }

    // ---------- small JS-like helpers ----------
    private static final String WS = "\\s\\u00a0\\ufeff\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000";

    static boolean isWs(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\uFEFF';
    }

    static String trimStart(String s) { int i = 0; while (i < s.length() && isWs(s.charAt(i))) i++; return s.substring(i); }
    static String trimEnd(String s) { int j = s.length(); while (j > 0 && isWs(s.charAt(j - 1))) j--; return s.substring(0, j); }
    static String trim(String s) { return trimStart(trimEnd(s)); }

    private static boolean on(Map<String, String> o, String k) { return "true".equals(o.get(k)); }
    private static String get(Map<String, String> o, String k) { String v = o.get(k); return v == null ? "" : v; }

    private static final Pattern FLOAT = Pattern.compile("^[\\s\\u00a0]*([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)");

    /** JS parseFloat: leading number or NaN. */
    static double parseFloatJs(String s) {
        if (s == null) return Double.NaN;
        Matcher m = FLOAT.matcher(s);
        if (!m.find()) return Double.NaN;
        try { return Double.parseDouble(m.group(1)); } catch (Exception e) { return Double.NaN; }
    }

    /** JS number -> string (1 -> "1", 1.2 -> "1.2"). */
    static String numStr(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        String s = Double.toString(d);
        return s;
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ---------- parse: plain text / HTML -> blocks ----------
    private static final Pattern NEWLINES = Pattern.compile("\\s*[\\r\\n]+\\s*");

    /** Plain text: every non-empty line is one block (same as pasted text lines in the HTML tool). */
    public static List<Block> parsePlain(String text) {
        List<Block> blocks = new ArrayList<Block>();
        if (text == null) return blocks;
        for (String line : text.split("\\r?\\n|\\r", -1)) {
            String t = line.replace('\u00a0', ' ');
            if (t.isEmpty()) continue;
            List<Run> r = new ArrayList<Run>();
            r.add(new Run(t, false, false, false));
            blocks.add(new Block(r, 0));
        }
        return blocks;
    }

    private static final Set<String> BLOCK = new HashSet<String>();
    private static final Set<String> VOID = new HashSet<String>();
    private static final Set<String> SKIP = new HashSet<String>();
    static {
        for (String s : new String[]{"P", "DIV", "LI", "UL", "OL", "BLOCKQUOTE", "TR", "TABLE", "PRE", "SECTION", "ARTICLE", "H1", "H2", "H3", "H4", "H5", "H6"}) BLOCK.add(s);
        for (String s : new String[]{"BR", "HR", "IMG", "META", "LINK", "INPUT", "COL", "WBR", "AREA", "BASE", "EMBED", "SOURCE", "TRACK", "PARAM"}) VOID.add(s);
        for (String s : new String[]{"SCRIPT", "STYLE", "HEAD", "TITLE", "TEMPLATE", "NOSCRIPT"}) SKIP.add(s);
    }

    private static final Pattern ATTR_STYLE = Pattern.compile("\\bstyle\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);

    private static String cssProp(String style, String prop) {
        if (style == null) return "";
        String result = "";
        for (String d : style.split(";")) {
            int k = d.indexOf(':');
            if (k < 0) continue;
            if (d.substring(0, k).trim().equalsIgnoreCase(prop)) result = d.substring(k + 1).replaceAll("(?i)\\s*!important\\s*$", "").trim().toLowerCase();
        }
        return result;
    }

    private static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) return s;
        Matcher m = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").matcher(s);
        StringBuilder sb = new StringBuilder(); int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start()); last = m.end();
            String e = m.group(1), rep = null;
            try {
                if (e.startsWith("#x") || e.startsWith("#X")) rep = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                else if (e.startsWith("#")) rep = new String(Character.toChars(Integer.parseInt(e.substring(1))));
                else if (e.equals("amp")) rep = "&"; else if (e.equals("lt")) rep = "<"; else if (e.equals("gt")) rep = ">";
                else if (e.equals("quot")) rep = "\""; else if (e.equals("apos")) rep = "'"; else if (e.equals("nbsp")) rep = "\u00a0";
                else if (e.equals("ndash")) rep = "\u2013"; else if (e.equals("mdash")) rep = "\u2014";
                else if (e.equals("hellip")) rep = "\u2026"; else if (e.equals("lsquo")) rep = "\u2018"; else if (e.equals("rsquo")) rep = "\u2019";
                else if (e.equals("ldquo")) rep = "\u201c"; else if (e.equals("rdquo")) rep = "\u201d";
            } catch (Exception ex) { rep = null; }
            sb.append(rep != null ? rep : m.group(0));
        }
        sb.append(s, last, s.length());
        return sb.toString();
    }

    private static final class Frame {
        final String tag; final boolean b, i, u, block; final int prevH;
        Frame(String tag, boolean b, boolean i, boolean u, boolean block, int prevH) { this.tag = tag; this.b = b; this.i = i; this.u = u; this.block = block; this.prevH = prevH; }
    }

    /** Mini HTML reader (clipboard HTML from Google Docs / Word / web, or HTML code). Same logic as parse() in the HTML tool. */
    public static List<Block> parseHtml(String html) {
        List<Block> blocks = new ArrayList<Block>();
        if (html == null) return blocks;
        List<Run> runs = new ArrayList<Run>();
        int[] h = {0};
        boolean b = false, i = false, u = false;
        List<Frame> stack = new ArrayList<Frame>();
        int n = html.length(), p = 0;
        int skipDepth = 0; String skipTag = null;
        while (p < n) {
            char c = html.charAt(p);
            if (c != '<') {
                int q = html.indexOf('<', p); if (q < 0) q = n;
                if (skipDepth == 0) {
                    String t = NEWLINES.matcher(decodeEntities(html.substring(p, q)).replace('\u00a0', ' ')).replaceAll(" ");
                    if (!t.isEmpty()) runs.add(new Run(t, b, i, u));
                }
                p = q; continue;
            }
            if (html.startsWith("<!--", p)) { int q = html.indexOf("-->", p + 4); p = q < 0 ? n : q + 3; continue; }
            int q = html.indexOf('>', p);
            if (q < 0) break;
            String raw = html.substring(p + 1, q); p = q + 1;
            if (raw.startsWith("!") || raw.startsWith("?")) continue;
            boolean closing = raw.startsWith("/");
            if (closing) raw = raw.substring(1);
            int e = 0; while (e < raw.length() && !Character.isWhitespace(raw.charAt(e)) && raw.charAt(e) != '/') e++;
            String tag = raw.substring(0, e).toUpperCase();
            if (tag.isEmpty()) continue;
            if (skipDepth > 0) {
                if (tag.equals(skipTag)) { if (closing) skipDepth--; else skipDepth++; }
                continue;
            }
            if (!closing && SKIP.contains(tag) && !raw.endsWith("/")) { skipTag = tag; skipDepth = 1; continue; }
            if (closing) {
                int idx = -1;
                for (int k = stack.size() - 1; k >= 0; k--) if (stack.get(k).tag.equals(tag)) { idx = k; break; }
                if (idx < 0) continue;
                while (stack.size() > idx) {
                    Frame f = stack.remove(stack.size() - 1);
                    if (f.block) { flush(blocks, runs, h[0]); h[0] = f.prevH; }
                    b = f.b; i = f.i; u = f.u;   // state before this tag opened
                }
                continue;
            }
            if (tag.equals("BR")) { flush(blocks, runs, h[0]); continue; }
            if (VOID.contains(tag) || raw.endsWith("/")) continue;
            // opening tag: compute new style (style attribute wins over the tag, like the HTML tool)
            Matcher sm = ATTR_STYLE.matcher(raw);
            String style = sm.find() ? (sm.group(1) != null ? sm.group(1) : sm.group(2)) : "";
            boolean b2 = b, i2 = i, u2 = u;
            String fw = cssProp(style, "font-weight");
            if (!fw.isEmpty()) { double w = parseFloatJs(fw); b2 = fw.equals("bold") || fw.equals("bolder") || (!Double.isNaN(w) && w >= 600); }
            else if (tag.equals("B") || tag.equals("STRONG")) b2 = true;
            String fst = cssProp(style, "font-style");
            if (!fst.isEmpty()) i2 = fst.equals("italic") || fst.equals("oblique");
            else if (tag.equals("I") || tag.equals("EM")) i2 = true;
            String td = cssProp(style, "text-decoration-line"); if (td.isEmpty()) td = cssProp(style, "text-decoration");
            if (!td.isEmpty()) u2 = td.contains("underline"); else if (tag.equals("U")) u2 = true;
            if (tag.equals("A")) u2 = false;   // links carry fake underline
            boolean isBlock = BLOCK.contains(tag);
            int prevH = h[0];
            if (isBlock) {
                flush(blocks, runs, h[0]);
                if (tag.length() == 2 && tag.charAt(0) == 'H' && Character.isDigit(tag.charAt(1))) h[0] = tag.charAt(1) - '0';
            }
            stack.add(new Frame(tag, b, i, u, isBlock, prevH));
            b = b2; i = i2; u = u2;
        }
        while (stack.size() > 0) { Frame f = stack.remove(stack.size() - 1); if (f.block) { flush(blocks, runs, h[0]); h[0] = f.prevH; } }
        flush(blocks, runs, h[0]);
        return blocks;
    }

    private static void flush(List<Block> blocks, List<Run> runs, int h) {
        if (!runs.isEmpty()) { blocks.add(new Block(new ArrayList<Run>(runs), h)); runs.clear(); }
    }

    // ---------- markdown, rules, auto heading ----------
    static final Pattern HRE = Pattern.compile(
        "^(?:(?:chapter|ch\\.?|episode|ep\\.?|part|volume|vol\\.?)\\s*(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[ivx]{1,5})\\b|prologue\\b|epilogue\\b|interlude\\b|\\u7b2c\\s*[0-9\\u96f6\\u3007\\u4e00\\u4e8c\\u4e24\\u5169\\u4e09\\u56db\\u4e94\\u516d\\u4e03\\u516b\\u4e5d\\u5341\\u767e\\u5343\\u4e07\\u842c\\uff10-\\uff19]+\\s*[\\u7ae0\\u56de\\u8282\\u7bc0\\u8bdd\\u8a71\\u5377\\u96c6\\u90e8])",
        Pattern.CASE_INSENSITIVE);
    static final Pattern SCENE = Pattern.compile("^([*\\-\\u2013\\u2014_=~#\\u2022]\\s*){3,}$");
    private static final String NS = "[^" + WS + "]";
    private static final Pattern MDRE = Pattern.compile(
        "(\\*\\*\\*|\\*\\*|\\*)(?=" + NS + ")([^*\\n]+?)(?<=" + NS + ")\\1|__(?=" + NS + ")([^_\\n]+?)(?<=" + NS + ")__|(?<![\\w])_(?=" + NS + ")([^_\\n]+?)(?<=" + NS + ")_(?![\\w])");

    static List<Run> mdRuns(List<Run> runs) {
        List<Run> out = new ArrayList<Run>();
        for (Run r : runs) {
            if (r.t.indexOf('*') < 0 && r.t.indexOf('_') < 0) { out.add(r); continue; }
            int last = 0; Matcher m = MDRE.matcher(r.t);
            while (m.find()) {
                if (m.start() > last) out.add(new Run(r.t.substring(last, m.start()), r.b, r.i, r.u));
                boolean b = r.b, i = r.i; String txt;
                if (m.group(1) != null) {
                    txt = m.group(2);
                    if (m.group(1).equals("***")) { b = true; i = true; } else if (m.group(1).equals("**")) b = true; else i = true;
                } else if (m.group(3) != null) { txt = m.group(3); b = true; }
                else { txt = m.group(4); i = true; }
                out.add(new Run(txt, b, i, r.u));
                last = m.end();
            }
            if (last < r.t.length()) out.add(new Run(r.t.substring(last), r.b, r.i, r.u));
        }
        return out;
    }

    /** Parsed "find => replace" rules. */
    public static final class Rules {
        final List<Pattern> reps = new ArrayList<Pattern>();
        final List<String> to = new ArrayList<String>();
        final List<Boolean> rx = new ArrayList<Boolean>();
        final List<Pattern> dels = new ArrayList<Pattern>();
        public int bad = 0;
        public int repCount() { return reps.size(); }
        public int delCount() { return dels.size(); }
    }

    private static final Pattern DELRE = Pattern.compile("^\\s*del-re:\\s*(.+?)\\s*$");
    private static final Pattern DELLIT = Pattern.compile("^\\s*del:\\s*(.+?)\\s*$");

    public static Rules parseRules(String src) {
        Rules R = new Rules();
        for (String line0 : String.valueOf(src == null ? "" : src).split("\n", -1)) {
            String line = line0.replace("\r", "");
            String tl = trim(line);
            if (tl.isEmpty() || tl.charAt(0) == '#') continue;
            try {
                Matcher m;
                if ((m = DELRE.matcher(line)).find()) { R.dels.add(Pattern.compile(m.group(1))); continue; }
                if ((m = DELLIT.matcher(line)).find()) { R.dels.add(Pattern.compile(Pattern.quote(m.group(1)))); continue; }
                int k = line.indexOf("=>");
                if (k < 0) { R.bad++; continue; }
                String f = trim(line.substring(0, k));
                String to = line.substring(k + 2);
                if (to.startsWith(" ")) to = to.substring(1);
                to = trimEnd(to);
                if (f.isEmpty()) { R.bad++; continue; }
                if (f.startsWith("re:")) { R.reps.add(Pattern.compile(trim(f.substring(3)))); R.to.add(to); R.rx.add(Boolean.TRUE); }
                else { R.reps.add(Pattern.compile(Pattern.quote(f))); R.to.add(to); R.rx.add(Boolean.FALSE); }
            } catch (Exception e) { R.bad++; }
        }
        return R;
    }

    /** JS-style replacement: $$ $& $1..$99 $<name> (unknown stays literal). */
    private static String expand(String to, Matcher m) {
        if (to.indexOf('$') < 0) return to;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < to.length(); i++) {
            char c = to.charAt(i);
            if (c != '$' || i + 1 >= to.length()) { sb.append(c); continue; }
            char d = to.charAt(i + 1);
            if (d == '$') { sb.append('$'); i++; }
            else if (d == '&') { sb.append(m.group()); i++; }
            else if (Character.isDigit(d)) {
                int num = d - '0', len = 1;
                if (i + 2 < to.length() && Character.isDigit(to.charAt(i + 2))) {
                    int two = num * 10 + (to.charAt(i + 2) - '0');
                    if (two >= 1 && two <= m.groupCount()) { num = two; len = 2; }
                }
                if (num >= 1 && num <= m.groupCount()) { String g = m.group(num); if (g != null) sb.append(g); i += len; }
                else sb.append(c);
            } else if (d == '<') {
                int close = to.indexOf('>', i + 2);
                if (close > 0) {
                    try { String g = m.group(to.substring(i + 2, close)); if (g != null) sb.append(g); i = close; }
                    catch (Exception e) { sb.append(c); }
                } else sb.append(c);
            } else sb.append(c);
        }
        return sb.toString();
    }

    static String applyRules(String t, Rules R) {
        for (int k = 0; k < R.reps.size(); k++) {
            Matcher m = R.reps.get(k).matcher(t);
            StringBuilder sb = new StringBuilder(); int last = 0;
            boolean rx = R.rx.get(k).booleanValue(); String to = R.to.get(k);
            while (m.find()) { sb.append(t, last, m.start()); sb.append(rx ? expand(to, m) : to); last = m.end(); }
            sb.append(t, last, t.length());
            t = sb.toString();
        }
        return t;
    }

    private static boolean delBlock(Block b, Rules R) {
        if (R.dels.isEmpty()) return false;
        StringBuilder sb = new StringBuilder();
        for (Run r : b.runs) sb.append(r.t);
        String t = sb.toString();
        for (Pattern p : R.dels) if (p.matcher(t).find()) return true;
        return false;
    }

    private static String clean(String v) { return trim((v == null ? "" : v).replaceAll("[^\\w.%\\-\\s,()]", "")); }

    // ---------- text fixes ----------
    private static final Pattern APOS = Pattern.compile("^(?:em\\b|tis\\b|twas\\b|twere\\b|twill\\b|til\\b|cause\\b|round\\b|bout\\b|n['\\u2019]|\\d\\d(?:s\\b|(?!\\d)))", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOTS2 = Pattern.compile("\\.(?: \\.){2,}");

    private static String fixText(String t, Map<String, String> o, int[] prev) {
        if (on(o, "zw")) t = t.replaceAll("[\\u200b-\\u200d\\u2060\\ufeff\\u00ad]", "").replaceAll("[\\u2000-\\u200a\\u202f\\u205f\\u3000]", " ");
        if (on(o, "dots")) t = t.replaceAll("\\.{3}", "\u2026");
        if (on(o, "dots")) t = DOTS2.matcher(t).replaceAll("\u2026").replaceAll("\\u3002{2,}", "\u2026").replaceAll("\\u2026{2,}", "\u2026");
        if (on(o, "dash")) t = t.replace("--", "\u2014");
        String q = get(o, "quotes");
        if (q.equals("straight")) t = t.replaceAll("[\\u201c\\u201d\\u201e]", "\"").replaceAll("[\\u2018\\u2019\\u201a]", "'");
        else if (q.equals("curly")) {
            StringBuilder r = new StringBuilder();
            for (int k = 0; k < t.length(); k++) {
                char c = t.charAt(k);
                int p = r.length() > 0 ? r.charAt(r.length() - 1) : prev[0];   // 0 = none
                boolean open = p == 0 || " \t\n\r\u000b\f\u00a0([{\u2013\u2014-\u201c\u2018".indexOf((char) p) >= 0 || isWs((char) p);
                if (c == '"' || c == '\u201c' || c == '\u201d') r.append(open ? '\u201c' : '\u201d');
                else if (c == '\'' || c == '\u2018' || c == '\u2019') {
                    if (open && APOS.matcher(t.substring(k + 1)).find()) r.append('\u2019');
                    else r.append(open ? '\u2018' : '\u2019');
                } else r.append(c);
            }
            t = r.toString();
        }
        if (!t.isEmpty()) prev[0] = t.charAt(t.length() - 1);
        return t;
    }

    private static String runsToHtml(List<Run> runs, Map<String, String> o, Rules R) {
        if (on(o, "md")) runs = mdRuns(runs);
        List<Run> m = new ArrayList<Run>();
        boolean kb = on(o, "keepB"), ki = on(o, "keepI"), ku = on(o, "keepU");
        for (Run r : runs) {
            Run x = new Run(r.t, r.b && kb, r.i && ki, r.u && ku);
            Run l = m.isEmpty() ? null : m.get(m.size() - 1);
            if (l != null && l.b == x.b && l.i == x.i && l.u == x.u) l.t += x.t; else m.add(x);
        }
        for (Run r : m) r.t = applyRules(r.t, R);
        while (!m.isEmpty() && trim(m.get(0).t).isEmpty()) m.remove(0);
        while (!m.isEmpty() && trim(m.get(m.size() - 1).t).isEmpty()) m.remove(m.size() - 1);
        if (m.isEmpty()) return "";
        m.get(0).t = trimStart(m.get(0).t);
        m.get(m.size() - 1).t = trimEnd(m.get(m.size() - 1).t);
        int[] prev = {0};
        StringBuilder html = new StringBuilder();
        for (Run r : m) {
            String s = esc(fixText(r.t, o, prev));
            if (s.isEmpty()) continue;
            if (r.u) s = "<u>" + s + "</u>";
            if (r.i) s = "<em>" + s + "</em>";
            if (r.b) s = "<strong>" + s + "</strong>";
            html.append(s);
        }
        String out = html.toString();
        if (on(o, "collapse")) out = out.replaceAll(" {2,}", " ");
        return out;
    }

    private static List<String> baseStyle(Map<String, String> o) {
        List<String> s = new ArrayList<String>();
        double sp = parseFloatJs(get(o, "spacing"));
        String lh = clean(get(o, "lh")), fs = clean(get(o, "fs"));
        if (!Double.isNaN(sp)) s.add("margin:0 0 " + numStr(sp) + get(o, "spacingUnit"));
        if (!lh.isEmpty()) s.add("line-height:" + lh);
        if (!fs.isEmpty()) s.add("font-size:" + fs);
        if (!get(o, "align").equals("none")) s.add("text-align:" + get(o, "align"));
        return s;
    }

    private static String indentCss(Map<String, String> o) {
        double v = parseFloatJs(get(o, "indentVal"));
        return (on(o, "indent") && v > 0) ? "text-indent:" + numStr(v) + get(o, "indentUnit") : "";
    }

    private static String join(List<String> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < l.size(); k++) { if (k > 0) sb.append(sep); sb.append(l.get(k)); }
        return sb.toString();
    }

    private static int countWords(String s) {
        int n = 0; boolean in = false;
        for (int k = 0; k < s.length(); k++) { boolean w = !isWs(s.charAt(k)); if (w && !in) n++; in = w; }
        return n;
    }

    // ---------- core generator ----------
    public static Result gen(Map<String, String> o, List<Block> blocks) {
        Rules R = parseRules(get(o, "rules"));
        List<String> parts = new ArrayList<String>();
        boolean first = true; int paras = 0, words = 0, nb = 0;
        List<String> base = baseStyle(o); String ind = indentCss(o);
        String gap = ""; for (String s : base) if (s.startsWith("margin")) { gap = s; break; }
        String mode = get(o, "mode");
        boolean useClass = mode.equals("class"), plain = mode.equals("plain");
        String autoH = get(o, "autoH");

        for (Block b : blocks) {
            if (delBlock(b, R)) continue;
            String html = runsToHtml(b.runs, o, R);
            String text = trim(html.replaceAll("<[^>]+>", ""));
            if (text.isEmpty()) continue;
            nb++;
            if (SCENE.matcher(text).find()) {
                String sc = get(o, "scene");
                if (sc.equals("remove")) { first = true; continue; }
                if (sc.equals("hr")) { parts.add("<hr>"); first = true; continue; }
                if (sc.equals("center")) {
                    List<String> st = new ArrayList<String>(); st.add("text-align:center");
                    if (!plain && !gap.isEmpty()) st.add(gap);
                    String sym = trim(get(o, "sceneText")); if (sym.isEmpty()) sym = "* * *";
                    parts.add("<p style=\"" + join(st, ";") + "\">" + esc(sym) + "</p>");
                    first = true; continue;
                }
            }
            int hl = b.h;
            if (hl == 0 && !autoH.isEmpty() && nb <= 3 && text.length() <= 120 && HRE.matcher(text).find()) hl = (int) parseFloatJs(autoH);
            if (hl != 0 && (b.h != 0 ? on(o, "headings") : true)) {
                parts.add("<h" + hl + (on(o, "hcenter") ? " style=\"text-align:center\"" : "") + ">" + html + "</h" + hl + ">");
                first = true; continue;
            }
            boolean noInd = first && on(o, "skipFirst");
            if (plain) parts.add("<p>" + html + "</p>");
            else if (useClass) parts.add("<p class=\"nv-p" + (noInd && !ind.isEmpty() ? " ni" : "") + "\">" + html + "</p>");
            else {
                List<String> st = new ArrayList<String>(base);
                if (!ind.isEmpty()) st.add(0, noInd ? "text-indent:0" : ind);
                parts.add("<p" + (st.isEmpty() ? "" : " style=\"" + join(st, ";") + "\"") + ">" + html + "</p>");
            }
            first = false; paras++; words += countWords(text);
        }

        String sepKey = get(o, "sep");
        String sep = sepKey.equals("none") ? "" : sepKey.equals("blank") ? "\n\n" : "\n";
        String out = join(parts, sep);
        if (useClass && !parts.isEmpty()) {
            List<String> rules = new ArrayList<String>(base); if (!ind.isEmpty()) rules.add(ind);
            out = "<style>.nv-p{" + join(rules, ";") + "}" + (ind.isEmpty() ? "" : ".nv-p.ni{text-indent:0}") + "</style>" + sep + out;
        }
        return new Result(out, paras, words, R.bad);
    }

    /** Inline-style HTML with single line breaks (used for rich copy and plain text, like inlineHtml() in the HTML tool). */
    public static String inlineHtml(Map<String, String> o, List<Block> blocks) {
        Map<String, String> c = new LinkedHashMap<String, String>(o);
        c.put("mode", "inline"); c.put("sep", "single");
        return gen(c, blocks).out;
    }

    /** Plain text: paragraphs separated by a blank line, scene <hr> becomes the symbol (same as plainText() in the HTML tool). */
    public static String plainText(Map<String, String> o, List<Block> blocks) {
        String html = inlineHtml(o, blocks);
        String sc = trim(get(o, "sceneText")); if (sc.isEmpty()) sc = "* * *";
        if (html.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(); boolean firstLine = true;
        for (String l : html.split("\n", -1)) {
            String t = l.equals("<hr>") ? sc : trim(l.replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&"));
            if (t.isEmpty()) continue;
            if (!firstLine) sb.append("\n\n");
            sb.append(t); firstLine = false;
        }
        return sb.toString();
    }

    /** WordPress block editor wrapper (same as the "Copy for WordPress block editor" button). */
    public static String wpBlock(String code) { return "<!-- wp:html -->\n" + code + "\n<!-- /wp:html -->"; }
}
