import com.mgb.keyboard.clip.ClipExport;
import com.mgb.keyboard.clip.ClipExport.Entry;
import com.mgb.keyboard.clip.ClipExport.Options;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/** v0.17.57 (C2): export to txt / docx. Prints FAIL lines; exit 1 on any fail. */
public class ClipExportTest {
    static int fails = 0, checks = 0;
    static void eq(String what, Object a, Object b) {
        checks++;
        if (a == null ? b != null : !a.equals(b)) { fails++; System.out.println("FAIL " + what + ": got " + a + " want " + b); }
    }
    static void ok(String what, boolean c) { eq(what, c, true); }

    static Entry e(String t) { return new Entry(t, null, false, 0L); }
    static Entry h(String t, String html) { return new Entry(t, html, false, 0L); }

    static Map<String, String> unzip(byte[] b) throws Exception {
        Map<String, String> m = new HashMap<String, String>();
        ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b));
        ZipEntry ze;
        while ((ze = z.getNextEntry()) != null) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int r;
            while ((r = z.read(buf)) > 0) o.write(buf, 0, r);
            m.put(ze.getName(), new String(o.toByteArray(), "UTF-8"));
        }
        return m;
    }

    static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
    }

    static String allText(Document d) {
        NodeList l = d.getElementsByTagNameNS("*", "t");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.getLength(); i++) sb.append(l.item(i).getTextContent()).append("|");
        return sb.toString();
    }

    static String docXml(List<Entry> es, Options o) throws Exception {
        byte[] b = ClipExport.toDocx(es, o);
        if (b == null) return null;
        return unzip(b).get("word/document.xml");
    }

    public static void main(String[] x) throws Exception {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Options o = new Options(); o.tz = utc; o.now = 1763137800000L;   // 2025-11-14 16:30 UTC
        Options plain = new Options(); plain.details = false; plain.tz = utc;

        // ---- prepare
        List<Entry> in = new ArrayList<Entry>();
        in.add(e("hello")); in.add(e("  ")); in.add(e("482913")); in.add(null); in.add(e("world"));
        int[] sk = new int[1];
        List<Entry> p = ClipExport.prepare(in, true, sk);
        eq("prep size skip", p.size(), 2); eq("prep skipped", sk[0], 1);
        eq("prep keep codes", ClipExport.prepare(in, false, sk).size(), 3); eq("prep skipped 0", sk[0], 0);
        eq("prep null", ClipExport.prepare(null, true, null).size(), 0);
        List<Entry> many = new ArrayList<Entry>();
        for (int i = 0; i < 700; i++) many.add(e("clip number " + i));
        eq("prep cap", ClipExport.prepare(many, false, null).size(), ClipExport.MAX_CLIPS);

        // ---- file name
        eq("name", ClipExport.fileName("txt", 1763137800000L, utc), "Jabra-Clips_2025-11-14_1630.txt");

        // ---- txt
        eq("txt plain 1", ClipExport.toTxt(java.util.Arrays.asList(e("a\nb")), plain), "a\nb\n");
        eq("txt plain 2", ClipExport.toTxt(java.util.Arrays.asList(e("a"), e("b")), plain), "a\n\n----------------------------------------\n\nb\n");
        eq("txt trailing nl", ClipExport.toTxt(java.util.Arrays.asList(e("a\n")), plain), "a\n");
        eq("txt empty", ClipExport.toTxt(new ArrayList<Entry>(), plain), "");
        eq("txt null", ClipExport.toTxt(null, null), "Jabra Clipboard\nExported  |  0 clips\n========================================\n\n");
        String t1 = ClipExport.toTxt(java.util.Arrays.asList(new Entry("hi", null, true, 1763137800000L)), o);
        ok("txt head", t1.startsWith("Jabra Clipboard\nExported 2025-11-14 16:30  |  1 clip\n"));
        ok("txt meta", t1.contains("#1  |  Pinned  |  2025-11-14 16:30\nhi\n"));
        byte[] tb = ClipExport.toTxtBytes(java.util.Arrays.asList(e("বাংলা")), plain);
        eq("bom 0", tb[0] & 0xFF, 0xEF); eq("bom 1", tb[1] & 0xFF, 0xBB); eq("bom 2", tb[2] & 0xFF, 0xBF);
        eq("bangla back", new String(tb, 3, tb.length - 3, "UTF-8"), "বাংলা\n");

        // ---- docx structure
        byte[] db = ClipExport.toDocx(java.util.Arrays.asList(e("one"), e("two")), o);
        ok("docx not null", db != null);
        Map<String, String> z = unzip(db);
        eq("docx parts", z.keySet().size(), 5);
        for (String k : new String[] { "[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels", "word/styles.xml" }) {
            ok("part " + k, z.containsKey(k));
            parse(z.get(k));   // well-formed or throws
        }
        eq("zip magic", (db[0] & 0xFF) + "," + (db[1] & 0xFF), "80,75");
        Document d = parse(z.get("word/document.xml"));
        String tx = allText(d);
        ok("docx title", tx.startsWith("Jabra Clipboard|Exported 2025-11-14 16:30  |  2 clips|#1|one|"));
        ok("docx has two", tx.contains("one|") && tx.contains("two|"));
        eq("docx empty list ok", docXml(new ArrayList<Entry>(), o) != null, true);
        parse(docXml(new ArrayList<Entry>(), plain));
        eq("docx null list", ClipExport.toDocx(null, null) != null, true);

        // ---- escaping + chars
        String nasty = "a & b < c > d \" e ' f \u0001 g \uD83D h \uD83D\uDE00 i \u200B বাংলা \tTAB\nline2";
        String dx = docXml(java.util.Arrays.asList(e(nasty)), plain);
        Document dd = parse(dx);
        String at = allText(dd);
        ok("esc amp", at.contains("a & b < c > d \" e ' f  g  h \uD83D\uDE00 i"));
        ok("bangla kept", at.contains("বাংলা"));
        ok("tab elem", dx.contains("<w:tab/>"));
        ok("line2 own para", at.contains("line2|"));
        eq("no raw ctrl", dx.indexOf('\u0001'), -1);

        // ---- html -> format
        String html = "<html><head><style>p{color:red}</style><title>x</title></head><body><!--StartFragment--><p>Hello <b>bold</b> and <i>it</i> <u>un</u> <s>st</s></p><p>2nd<br>line</p>"
            + "<ul><li>one</li><li>two</li></ul><ol><li>a</li><li>b</li></ol><h2>Head</h2><pre>x = 1\n\ny = 2</pre><code>mono</code>"
            + "<span style=\"font-weight:700;font-style:italic;text-decoration:underline line-through;color:#ffffff\">sty</span> &amp;&lt;b&gt;&#x1F600;&nbsp;z<!--EndFragment--></body></html>";
        String hx = docXml(java.util.Arrays.asList(h("ignored plain", html)), plain);
        parse(hx);
        ok("h bold", hx.contains("<w:b/><w:bCs/></w:rPr><w:t xml:space=\"preserve\">bold</w:t>"));
        ok("h italic", hx.contains("<w:i/><w:iCs/></w:rPr><w:t xml:space=\"preserve\">it</w:t>"));
        ok("h under", hx.contains("<w:u w:val=\"single\"/></w:rPr><w:t xml:space=\"preserve\">un</w:t>"));
        ok("h strike", hx.contains("<w:strike/></w:rPr><w:t xml:space=\"preserve\">st</w:t>"));
        ok("h br", hx.contains("<w:br/>"));
        ok("h bullet", hx.contains(">\u2022 </w:t></w:r><w:r><w:t xml:space=\"preserve\">one<") && hx.contains(">two<"));
        ok("h numbered", hx.contains(">1. </w:t></w:r><w:r><w:t xml:space=\"preserve\">a<") && hx.contains(">2. </w:t>"));
        ok("h heading bold", hx.contains("<w:b/><w:bCs/></w:rPr><w:t xml:space=\"preserve\">Head<"));
        ok("h mono", hx.contains("Courier New"));
        ok("h pre blank line", hx.contains(">x = 1<") && hx.contains(">y = 2<"));
        ok("h span style", hx.contains("<w:b/><w:bCs/><w:i/><w:iCs/><w:strike/><w:u w:val=\"single\"/></w:rPr><w:t xml:space=\"preserve\">sty<"));
        ok("h no color", !hx.contains("FFFFFF") && !hx.contains("ffffff") && !hx.contains("<w:color"));
        ok("h entities", hx.contains("&amp;&lt;b&gt;\uD83D\uDE00 z"));
        ok("h skips style/title", !hx.contains("color:red") && !hx.contains(">x<"));
        ok("h not plain", !hx.contains("ignored plain"));

        // format OFF -> plain text used
        Options nf = new Options(); nf.details = false; nf.format = false; nf.tz = utc;
        String fx = docXml(java.util.Arrays.asList(h("plain text", "<b>bold</b>")), nf);
        ok("fmt off plain", fx.contains("plain text") && !fx.contains("<w:b/>"));
        // empty html result -> fallback to plain
        String ex = docXml(java.util.Arrays.asList(h("fallback me", "<style>p{}</style>")), plain);
        ok("html empty fallback", ex.contains("fallback me"));
        String ux = docXml(java.util.Arrays.asList(h("fallback two", "<b>unclosed <i>x")), plain);
        parse(ux); ok("unclosed ok", ux.contains(">unclosed <") || ux.contains("unclosed"));
        parse(docXml(java.util.Arrays.asList(h("t", "<<<>>> & <a href='x' <b>")), plain));

        // meta details on in docx
        String mx = docXml(java.util.Arrays.asList(new Entry("pin me", null, true, 1763137800000L)), o);
        ok("meta in docx", mx.contains("#1  |  Pinned  |  2025-11-14 16:30"));
        ok("grey meta", mx.contains("<w:color w:val=\"808080\"/>"));
        // separator only between clips
        String sx = docXml(java.util.Arrays.asList(e("a"), e("b"), e("c")), plain);
        int seps = 0, ix = 0;
        while ((ix = sx.indexOf("<w:pBdr>", ix)) >= 0) { seps++; ix++; }
        eq("separators", seps, 2);

        // ---- big + fuzz
        long t0 = System.currentTimeMillis();
        List<Entry> big = new ArrayList<Entry>();
        for (int i = 0; i < 500; i++) big.add(h("clip " + i + " & <x>", "<p><b>clip " + i + "</b> &amp; text</p>"));
        byte[] bigb = ClipExport.toDocx(big, o);
        ok("big ok", bigb != null); parse(unzip(bigb).get("word/document.xml"));
        ok("big fast", System.currentTimeMillis() - t0 < 3000);
        String[] tok = { "<b>", "</b>", "<i>", "</i>", "<p>", "</p>", "<br>", "<br/>", "<li>", "<ul>", "</ul>", "<ol>", "<td>", "</td>", "&amp;", "&#x1F600;", "&#99999999;", "&bad", "<", ">", "text", "বাংলা",
            "<script>", "</script>", "<style>", "</style>", "\n", "\t", " ", "<pre>", "</pre>", "\uD83D", "\u0000", "<span style='font-weight:bold'>", "</span>", "<!--", "-->", "<div/>", "<h1>", "</h1>", "'", "\"" };
        Random r = new Random(7);
        for (int it = 0; it < 3000; it++) {
            StringBuilder sb = new StringBuilder();
            int n = r.nextInt(30);
            for (int k = 0; k < n; k++) sb.append(tok[r.nextInt(tok.length)]);
            String s = sb.toString();
            String dxx = null;
            try { dxx = docXml(java.util.Arrays.asList(h(s.length() == 0 ? "x" : s, s)), o); parse(dxx); }
            catch (Throwable ex2) { fails++; checks++; System.out.println("FAIL fuzz " + it + " " + ex2 + " for " + s.replace("\n", "\\n")); break; }
            checks++;
        }

        System.out.println("ClipExportTest: " + checks + " checks, " + fails + " fails");
        if (fails > 0) System.exit(1);
    }
}
