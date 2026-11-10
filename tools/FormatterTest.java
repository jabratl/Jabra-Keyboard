import com.mgb.keyboard.formatter.NovelFormatter;
import com.mgb.keyboard.formatter.NovelFormatter.*;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Compares the Java engine with the ORIGINAL JS output. Usage: java -cp out FormatterTest cases.txt   (no args = built-in checks only) */
public class FormatterTest {
    static int pass = 0, fail = 0;
    static String dec(String s) throws Exception { return URLDecoder.decode(s, "UTF-8"); }
    static void check(String name, Object got, Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) pass++;
        else { fail++; System.out.println("FAIL " + name + "\n  got : " + got + "\n  want: " + want); }
    }
    public static void main(String[] a) throws Exception {
        if (a.length > 0) fileCases(a[0]);
        builtin();
        System.out.println(pass + "/" + (pass + fail) + " passed");
        if (fail > 0) System.exit(1);
    }
    static void fileCases(String f) throws Exception {
        List<String> L = Files.readAllLines(Paths.get(f), StandardCharsets.UTF_8);
        Map<String, String> o = null; List<Block> bl = null; int n = 0, shown = 0;
        for (String l : L) {
            if (l.equals("CASE")) { o = new LinkedHashMap<String, String>(); bl = new ArrayList<Block>(); n++; }
            else if (l.startsWith("O\t")) { for (String kv : l.substring(2).split("\t", -1)) { int k = kv.indexOf('='); o.put(kv.substring(0, k), dec(kv.substring(k + 1))); } }
            else if (l.startsWith("B\t")) {
                String[] p = l.split("\t", -1); List<Run> runs = new ArrayList<Run>();
                for (int i = 2; i < p.length; i++) { String r = p[i]; runs.add(new Run(dec(r.substring(4)), r.charAt(0) == '1', r.charAt(1) == '1', r.charAt(2) == '1')); }
                bl.add(new Block(runs, Integer.parseInt(p[1])));
            } else if (l.startsWith("E\t")) {
                String[] p = l.split("\t", -1);
                Result r = NovelFormatter.gen(o, bl);
                boolean ok = r.out.equals(dec(p[1])) && r.paras == Integer.parseInt(p[2]) && r.words == Integer.parseInt(p[3]) && r.bad == Integer.parseInt(p[4]);
                if (ok) pass++; else { fail++; if (shown++ < 5) System.out.println("FAIL case " + n + " opts=" + o + "\n  got : " + r.out + " | " + r.paras + "/" + r.words + "/" + r.bad + "\n  want: " + dec(p[1]) + " | " + p[2] + "/" + p[3] + "/" + p[4]); }
            }
        }
        System.out.println("differential cases vs original JS: " + n);
    }
    static Map<String, String> D() { return NovelFormatter.defaults(); }
    static void builtin() {
        // HTML parser: Google Docs style clipboard
        String gd = "<meta charset='utf-8'><html><head><title>x</title><style>p{color:red}</style></head><body><b style=\"font-weight:normal;\" id=\"docs-internal-guid-1\">"
            + "<h1 dir=\"ltr\"><span style=\"font-weight:700\">Chapter 1</span></h1>"
            + "<p dir=\"ltr\"><span style=\"font-style:italic\">Hello</span><span> world &amp; </span><span style=\"font-weight:700;text-decoration:underline\">bold</span></p>"
            + "<p>Line one<br>Line two</p><p><a href=\"x\" style=\"text-decoration:underline\">link</a></p><!--StartFragment--></b></body></html>";
        List<Block> b = NovelFormatter.parseHtml(gd);
        check("gdocs blocks", b.size(), 5);
        check("gdocs h1", b.get(0).h, 1);
        Result r = NovelFormatter.gen(D(), b);
        check("gdocs out", r.out,
            "<h1><strong>Chapter 1</strong></h1>\n<p style=\"text-indent:0;margin:0 0 1em\"><em>Hello</em> world &amp; <strong><u>bold</u></strong></p>\n<p style=\"text-indent:2em;margin:0 0 1em\">Line one</p>\n<p style=\"text-indent:2em;margin:0 0 1em\">Line two</p>\n<p style=\"text-indent:2em;margin:0 0 1em\">link</p>");
        // plain text
        check("plain split", NovelFormatter.parsePlain("a\n\nb\r\nc").size(), 3);
        // plain text output + wp
        Map<String, String> o = D(); o.put("scene", "hr");
        check("plainText", NovelFormatter.plainText(o, NovelFormatter.parsePlain("One\n***\nTwo & <x>")), "One\n\n* * *\n\nTwo & <x>");
        check("wp", NovelFormatter.wpBlock("<p>x</p>"), "<!-- wp:html -->\n<p>x</p>\n<!-- /wp:html -->");
        // empty input
        check("empty", NovelFormatter.gen(D(), NovelFormatter.parsePlain("")).out, "");
        // entities and skip tags
        check("entities", NovelFormatter.parseHtml("<p>a&nbsp;b &#8212; &lt;c&gt;</p><script>bad()</script>").get(0).runs.get(0).t, "a b \u2014 <c>");
        // junk must not throw
        try { NovelFormatter.parseHtml("<<<>>><p<b>>x</p></div></i><p style='font-weight:'>y"); NovelFormatter.gen(D(), NovelFormatter.parseHtml("<p>")); pass++; } catch (Throwable t) { fail++; System.out.println("FAIL junk html threw " + t); }
    }
}
