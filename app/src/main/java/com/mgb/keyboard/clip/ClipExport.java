package com.mgb.keyboard.clip;

import com.mgb.keyboard.typing.ClipSensitive;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * v0.17.57 (C2): export clips to .txt and .docx. Pure Java (java.util.zip only, no library, no Android), tools/ClipExportTest.java.
 * TXT = plain UTF-8 text (a text file cannot hold format). DOCX = a small valid Word file; clips that have a stored html format
 * (ClipFmt) keep bold / italic / underline / strike / monospace, headings, lists and line breaks. Colours and backgrounds are
 * left out ON PURPOSE (white text copied from a dark app would be invisible on a white page). Never throws: problems = plain text / null.
 */
public final class ClipExport {
    private ClipExport() { }

    public static final int MAX_CLIPS = 500;
    public static final String MIME_TXT = "text/plain";
    public static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static final class Entry {
        public final String text, html;
        public final boolean pinned;
        public final long made;   // ms, 0 = unknown
        public Entry(String text, String html, boolean pinned, long made) {
            this.text = text == null ? "" : text; this.html = html; this.pinned = pinned; this.made = made;
        }
    }

    public static final class Options {
        public boolean details = true;    // header + per clip line: number, Pinned, date
        public boolean format = true;     // docx: use stored html format
        public boolean skipCode = true;   // leave out code-like clips (OTP / PIN, ClipSensitive)
        public long now = 0L;             // export time (ms)
        public TimeZone tz = TimeZone.getDefault();
    }

    // ---------------------------------------------------------------- helpers
    /** Drops empty clips (and code-like ones when skipCode); caps at MAX_CLIPS. skipped[0] = how many code-like were left out. */
    public static List<Entry> prepare(List<Entry> in, boolean skipCode, int[] skipped) {
        ArrayList<Entry> out = new ArrayList<Entry>();
        int sk = 0;
        if (in != null) for (Entry e : in) {
            if (e == null || e.text.trim().length() == 0) continue;
            if (skipCode && ClipSensitive.looksLikeCode(e.text)) { sk++; continue; }
            if (out.size() >= MAX_CLIPS) break;
            out.add(e);
        }
        if (skipped != null && skipped.length > 0) skipped[0] = sk;
        return out;
    }

    public static String fileName(String ext, long now, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US);
        f.setTimeZone(tz == null ? TimeZone.getDefault() : tz);
        return "Jabra-Clips_" + f.format(new Date(now)) + "." + ext;
    }

    private static String date(long ms, TimeZone tz) {
        if (ms <= 0L) return "";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        f.setTimeZone(tz == null ? TimeZone.getDefault() : tz);
        return f.format(new Date(ms));
    }

    private static String metaLine(int n, Entry e, Options o) {
        StringBuilder sb = new StringBuilder("#").append(n);
        if (e.pinned) sb.append("  |  Pinned");
        String d = date(e.made, o.tz);
        if (d.length() > 0) sb.append("  |  ").append(d);
        return sb.toString();
    }

    private static String headLine(int count, Options o) {
        String d = date(o.now, o.tz);
        return (d.length() > 0 ? "Exported " + d + "  |  " : "Exported  |  ") + count + (count == 1 ? " clip" : " clips");
    }

    // ---------------------------------------------------------------- TXT
    public static String toTxt(List<Entry> es, Options o) {
        if (o == null) o = new Options();
        StringBuilder sb = new StringBuilder();
        int n = es == null ? 0 : es.size();
        String line = "----------------------------------------";
        if (o.details) sb.append("Jabra Clipboard\n").append(headLine(n, o)).append("\n").append("========================================\n\n");
        for (int i = 0; i < n; i++) {
            Entry e = es.get(i);
            if (o.details) sb.append(metaLine(i + 1, e, o)).append("\n");
            sb.append(e.text);
            if (e.text.endsWith("\n")) sb.setLength(sb.length() - 1);
            sb.append("\n");
            if (i < n - 1) sb.append("\n").append(line).append("\n\n");
        }
        return sb.toString();
    }

    /** UTF-8 bytes with a BOM (old Windows Notepad needs it to show Bangla correctly). */
    public static byte[] toTxtBytes(List<Entry> es, Options o) {
        byte[] body = toTxt(es, o).getBytes(UTF8);
        byte[] out = new byte[body.length + 3];
        out[0] = (byte) 0xEF; out[1] = (byte) 0xBB; out[2] = (byte) 0xBF;
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }

    // ---------------------------------------------------------------- DOCX
    static final class Run {
        String t = ""; boolean b, i, u, s, mono, br, tab;
        int sz = 0; String color = null;   // sz in half-points, color hex (only our own grey, never from html)
    }

    /** Valid .docx bytes, or null on a problem. */
    public static byte[] toDocx(List<Entry> es, Options o) {
        try {
            if (o == null) o = new Options();
            int n = es == null ? 0 : es.size();
            StringBuilder body = new StringBuilder();
            if (o.details) {
                body.append(para(runs(txtRun("Jabra Clipboard", true, 32, null)), 60));
                body.append(para(runs(txtRun(headLine(n, o), false, 18, "808080")), 200));
            }
            for (int i = 0; i < n; i++) {
                Entry e = es.get(i);
                if (o.details) body.append(para(runs(txtRun(metaLine(i + 1, e, o), true, 18, "808080")), 60));
                List<List<Run>> ps = null;
                if (o.format && e.html != null && e.html.length() > 0) ps = parseHtml(e.html);
                if (ps == null || ps.isEmpty()) ps = plainParas(e.text);
                for (List<Run> p : ps) body.append(para(p, 0));
                if (i < n - 1) body.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"1\" w:color=\"BFBFBF\"/></w:pBdr><w:spacing w:before=\"120\" w:after=\"240\"/></w:pPr></w:p>");
            }
            if (n == 0) body.append("<w:p/>");
            String doc = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + body
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ZipOutputStream z = new ZipOutputStream(bo);
            put(z, "[Content_Types].xml", CONTENT_TYPES);
            put(z, "_rels/.rels", RELS);
            put(z, "word/document.xml", doc);
            put(z, "word/_rels/document.xml.rels", DOC_RELS);
            put(z, "word/styles.xml", STYLES);
            z.close();
            return bo.toByteArray();
        } catch (Throwable t) { return null; }
    }

    private static void put(ZipOutputStream z, String name, String xml) throws java.io.IOException {
        ZipEntry ze = new ZipEntry(name);
        z.putNextEntry(ze);
        z.write(xml.getBytes(UTF8));
        z.closeEntry();
    }

    private static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String CONTENT_TYPES = XML_HEAD + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>"
        + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
        + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/></Types>";
    private static final String RELS = XML_HEAD + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>";
    private static final String DOC_RELS = XML_HEAD + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>";
    private static final String STYLES = XML_HEAD + "<w:styles xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
        + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Nirmala UI\"/><w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:lang w:val=\"en-US\" w:eastAsia=\"en-US\" w:bidi=\"bn-BD\"/></w:rPr></w:rPrDefault>"
        + "<w:pPrDefault><w:pPr><w:spacing w:after=\"0\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
        + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style></w:styles>";

    private static List<Run> runs(Run... r) { ArrayList<Run> l = new ArrayList<Run>(); for (Run x : r) l.add(x); return l; }

    private static Run txtRun(String t, boolean bold, int sz, String color) {
        Run r = new Run(); r.t = t; r.b = bold; r.sz = sz; r.color = color; return r;
    }

    private static List<List<Run>> plainParas(String text) {
        ArrayList<List<Run>> out = new ArrayList<List<Run>>();
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        if (t.endsWith("\n")) t = t.substring(0, t.length() - 1);
        int from = 0;
        while (true) {
            int k = t.indexOf('\n', from);
            String line = k < 0 ? t.substring(from) : t.substring(from, k);
            ArrayList<Run> l = new ArrayList<Run>();
            splitTabs(line, l, false, false, false, false, false);
            out.add(l);
            if (k < 0) break;
            from = k + 1;
        }
        return out;
    }

    private static void splitTabs(String s, List<Run> to, boolean b, boolean i, boolean u, boolean st, boolean mono) {
        int from = 0;
        while (true) {
            int k = s.indexOf('\t', from);
            String part = k < 0 ? s.substring(from) : s.substring(from, k);
            if (part.length() > 0) { Run r = new Run(); r.t = part; r.b = b; r.i = i; r.u = u; r.s = st; r.mono = mono; to.add(r); }
            if (k < 0) break;
            Run t = new Run(); t.tab = true; to.add(t);
            from = k + 1;
        }
    }

    private static String para(List<Run> rs, int afterTwips) {
        StringBuilder sb = new StringBuilder("<w:p>");
        if (afterTwips > 0) sb.append("<w:pPr><w:spacing w:after=\"").append(afterTwips).append("\"/></w:pPr>");
        for (Run r : rs) runXml(sb, r);
        return sb.append("</w:p>").toString();
    }

    private static void runXml(StringBuilder sb, Run r) {
        if (r.br) { sb.append("<w:r><w:br/></w:r>"); return; }
        if (r.tab) { sb.append("<w:r><w:tab/></w:r>"); return; }
        if (r.t.length() == 0) return;
        StringBuilder pr = new StringBuilder();
        if (r.mono) pr.append("<w:rFonts w:ascii=\"Courier New\" w:hAnsi=\"Courier New\" w:cs=\"Courier New\"/>");
        if (r.b) pr.append("<w:b/><w:bCs/>");
        if (r.i) pr.append("<w:i/><w:iCs/>");
        if (r.s) pr.append("<w:strike/>");
        if (r.color != null) pr.append("<w:color w:val=\"").append(r.color).append("\"/>");
        if (r.sz > 0) pr.append("<w:sz w:val=\"").append(r.sz).append("\"/><w:szCs w:val=\"").append(r.sz).append("\"/>");
        if (r.u) pr.append("<w:u w:val=\"single\"/>");
        sb.append("<w:r>");
        if (pr.length() > 0) sb.append("<w:rPr>").append(pr).append("</w:rPr>");
        sb.append("<w:t xml:space=\"preserve\">").append(xmlEsc(r.t)).append("</w:t></w:r>");
    }

    /** XML 1.0 safe text: bad control chars and lone surrogates are dropped, & < > " ' escaped. */
    static String xmlEsc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) { sb.append(c).append(s.charAt(i + 1)); i++; }
                continue;
            }
            if (Character.isLowSurrogate(c)) continue;
            if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD)) {
                switch (c) {
                    case '&': sb.append("&amp;"); break;
                    case '<': sb.append("&lt;"); break;
                    case '>': sb.append("&gt;"); break;
                    case '"': sb.append("&quot;"); break;
                    case '\'': sb.append("&apos;"); break;
                    default: sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- html -> paragraphs of runs
    private static final class Frame { String tag; boolean b, i, u, s, mono; }

    static List<List<Run>> parseHtml(String h) {
        ArrayList<List<Run>> paras = new ArrayList<List<Run>>();
        try {
            ArrayList<Run> cur = new ArrayList<Run>();
            ArrayList<Frame> st = new ArrayList<Frame>();
            ArrayList<int[]> lists = new ArrayList<int[]>();   // [0] = ordered?1:0, [1] = counter
            int skip = 0, pre = 0;
            int p = 0, n = h.length();
            while (p < n) {
                char c = h.charAt(p);
                if (c == '<') {
                    if (h.startsWith("<!--", p)) { int e = h.indexOf("-->", p + 4); p = e < 0 ? n : e + 3; continue; }
                    int e = h.indexOf('>', p + 1);
                    if (e < 0) { p = n; break; }
                    String raw = h.substring(p + 1, e);
                    p = e + 1;
                    boolean closing = raw.startsWith("/");
                    int a = closing ? 1 : 0, z = a;
                    while (z < raw.length() && !Character.isWhitespace(raw.charAt(z)) && raw.charAt(z) != '/') z++;
                    String tag = raw.substring(a, z).toLowerCase(Locale.US);
                    if (tag.length() == 0 || tag.startsWith("!") || tag.startsWith("?")) continue;
                    String attrs = raw.substring(z).toLowerCase(Locale.US);
                    if (tag.equals("style") || tag.equals("script") || tag.equals("head") || tag.equals("title")) { skip = Math.max(0, skip + (closing ? -1 : 1)); continue; }
                    if (skip > 0) continue;
                    if (closing) {
                        if (isBlock(tag)) { flush(paras, cur); if (tag.equals("pre")) pre = Math.max(0, pre - 1);
                            if ((tag.equals("ul") || tag.equals("ol")) && !lists.isEmpty()) lists.remove(lists.size() - 1); }
                        else if (tag.equals("td") || tag.equals("th")) { Run t = new Run(); t.tab = true; cur.add(t); }
                        for (int k = st.size() - 1; k >= 0; k--) if (st.get(k).tag.equals(tag)) { while (st.size() > k) st.remove(st.size() - 1); break; }
                        continue;
                    }
                    if (tag.equals("br")) { Run t = new Run(); t.br = true; cur.add(t); continue; }
                    if (tag.equals("hr")) { flush(paras, cur); continue; }
                    if (isBlock(tag)) {
                        flush(paras, cur);
                        if (tag.equals("pre")) pre++;
                        if (tag.equals("ul") || tag.equals("ol")) lists.add(new int[] { tag.equals("ol") ? 1 : 0, 0 });
                        if (tag.equals("li")) {
                            Run t = new Run();
                            if (!lists.isEmpty() && lists.get(lists.size() - 1)[0] == 1) t.t = (++lists.get(lists.size() - 1)[1]) + ". ";
                            else t.t = "\u2022 ";
                            cur.add(t);
                        }
                    }
                    Frame f = new Frame(); f.tag = tag;
                    if (tag.equals("b") || tag.equals("strong") || (tag.length() == 2 && tag.charAt(0) == 'h' && Character.isDigit(tag.charAt(1)))) f.b = true;
                    if (tag.equals("i") || tag.equals("em") || tag.equals("cite") || tag.equals("dfn")) f.i = true;
                    if (tag.equals("u") || tag.equals("ins")) f.u = true;
                    if (tag.equals("s") || tag.equals("strike") || tag.equals("del")) f.s = true;
                    if (tag.equals("code") || tag.equals("pre") || tag.equals("tt") || tag.equals("kbd") || tag.equals("samp")) f.mono = true;
                    if (attrs.indexOf("style") >= 0) {
                        if (attrs.matches("(?s).*font-weight\\s*:\\s*(bold|bolder|[6-9]00).*")) f.b = true;
                        if (attrs.matches("(?s).*font-style\\s*:\\s*(italic|oblique).*")) f.i = true;
                        if (attrs.matches("(?s).*text-decoration[^;\"']*underline.*")) f.u = true;
                        if (attrs.matches("(?s).*text-decoration[^;\"']*line-through.*")) f.s = true;
                        if (attrs.matches("(?s).*font-family\\s*:[^;\"']*(monospace|courier|consolas).*")) f.mono = true;
                    }
                    boolean selfClose = raw.endsWith("/");
                    if (!selfClose && !isVoid(tag)) st.add(f);
                    continue;
                }
                int e = h.indexOf('<', p);
                if (e < 0) e = n;
                String txt = decode(h.substring(p, e));
                p = e;
                if (skip > 0 || txt.length() == 0) continue;
                boolean b = false, i = false, u = false, s = false, m = false;
                for (Frame f : st) { b |= f.b; i |= f.i; u |= f.u; s |= f.s; m |= f.mono; }
                if (pre > 0) {
                    String[] ls = txt.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
                    for (int k = 0; k < ls.length; k++) {
                        if (k > 0) { if (cur.isEmpty()) cur.add(txtRun("", false, 0, null)); flushForce(paras, cur); }
                        splitTabs(ls[k], cur, b, i, u, s, m);
                    }
                } else {
                    String t = txt.replaceAll("[ \\t\\r\\n\\u00A0]+", " ");
                    if (t.startsWith(" ") && (cur.isEmpty() || endsWithSpace(cur))) t = t.substring(1);
                    if (t.length() > 0) splitTabs(t, cur, b, i, u, s, m);
                }
            }
            flush(paras, cur);
        } catch (Throwable t) { paras.clear(); }
        return paras;
    }

    private static boolean endsWithSpace(List<Run> cur) {
        Run r = cur.get(cur.size() - 1);
        return r.br || r.tab || r.t.endsWith(" ") || r.t.endsWith("\u2022 ");
    }

    private static boolean isBlock(String t) {
        return t.equals("p") || t.equals("div") || t.equals("li") || t.equals("ul") || t.equals("ol") || t.equals("tr") || t.equals("table")
            || t.equals("pre") || t.equals("blockquote") || (t.length() == 2 && t.charAt(0) == 'h' && Character.isDigit(t.charAt(1)))
            || t.equals("section") || t.equals("article") || t.equals("body") || t.equals("html");
    }

    private static boolean isVoid(String t) { return t.equals("img") || t.equals("meta") || t.equals("link") || t.equals("input") || t.equals("wbr"); }

    /** Ends the current paragraph if it has real content. */
    private static void flush(List<List<Run>> paras, ArrayList<Run> cur) {
        boolean has = false;
        for (Run r : cur) if (r.br || r.tab || r.t.trim().length() > 0) { has = true; break; }
        if (has) {
            Run last = cur.get(cur.size() - 1);
            if (!last.br && !last.tab && last.t.endsWith(" ")) last.t = last.t.replaceAll(" +$", "");
            paras.add(new ArrayList<Run>(cur));
        }
        cur.clear();
    }

    /** Ends the current paragraph even when it is empty (blank line inside <pre>). */
    private static void flushForce(List<List<Run>> paras, ArrayList<Run> cur) {
        paras.add(new ArrayList<Run>(cur));
        cur.clear();
    }

    private static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '&') { sb.append(c); continue; }
            int e = s.indexOf(';', i + 1);
            if (e < 0 || e - i > 10) { sb.append(c); continue; }
            String name = s.substring(i + 1, e);
            String rep = null;
            if (name.startsWith("#")) {
                try {
                    int cp = (name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X')) ? Integer.parseInt(name.substring(2), 16) : Integer.parseInt(name.substring(1));
                    if (cp > 0 && cp <= 0x10FFFF) rep = new String(Character.toChars(cp));
                } catch (Throwable t) { rep = null; }
            } else if (name.equals("amp")) rep = "&"; else if (name.equals("lt")) rep = "<"; else if (name.equals("gt")) rep = ">";
            else if (name.equals("quot")) rep = "\""; else if (name.equals("apos")) rep = "'"; else if (name.equals("nbsp")) rep = " ";
            else if (name.equals("ndash")) rep = "\u2013"; else if (name.equals("mdash")) rep = "\u2014"; else if (name.equals("hellip")) rep = "\u2026";
            else if (name.equals("lsquo")) rep = "\u2018"; else if (name.equals("rsquo")) rep = "\u2019"; else if (name.equals("ldquo")) rep = "\u201C";
            else if (name.equals("rdquo")) rep = "\u201D"; else if (name.equals("bull")) rep = "\u2022"; else if (name.equals("middot")) rep = "\u00B7";
            else if (name.equals("copy")) rep = "\u00A9"; else if (name.equals("reg")) rep = "\u00AE"; else if (name.equals("trade")) rep = "\u2122";
            if (rep == null) { sb.append(c); continue; }
            sb.append(rep);
            i = e;
        }
        return sb.toString();
    }
}
