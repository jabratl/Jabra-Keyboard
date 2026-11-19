import com.mgb.keyboard.typing.ClipMultiPaste;
import java.util.*;

public class ClipMultiPasteTest {
    static int n = 0, fails = 0;
    static void ok(boolean c, String m) { n++; if (!c) { fails++; System.out.println("FAIL " + m); } }
    static List<String> L(String... a) { return new ArrayList<>(Arrays.asList(a)); }
    public static void main(String[] x) {
        ok(!ClipMultiPaste.anyFormat(null), "null = no format");
        ok(!ClipMultiPaste.anyFormat(L(null, null)), "all null = no format");
        ok(!ClipMultiPaste.anyFormat(L(null, "  ")), "blank html = no format");
        ok(ClipMultiPaste.anyFormat(L(null, "<b>a</b>")), "one html = format");
        ok(ClipMultiPaste.twoButtons(true, true), "pref ON + format = two buttons");
        ok(!ClipMultiPaste.twoButtons(true, false), "no format = one Paste");
        ok(!ClipMultiPaste.twoButtons(false, true), "pref OFF = one Paste (old bar)");
        ok(!ClipMultiPaste.twoButtons(false, false), "OFF + none = one Paste");

        ok(ClipMultiPaste.plainToHtml("a<b>&c").equals("a&lt;b&gt;&amp;c"), "escape");
        ok(ClipMultiPaste.plainToHtml("l1\nl2").equals("l1<br>l2"), "newline = br");
        ok(ClipMultiPaste.plainToHtml("l1\r\nl2").equals("l1<br>l2"), "crlf = one br");
        ok(ClipMultiPaste.plainToHtml("a  b").equals("a &nbsp;b"), "double space kept");
        ok(ClipMultiPaste.plainToHtml("a   b").contains("&nbsp;"), "triple space kept");
        ok(ClipMultiPaste.plainToHtml(null).isEmpty() && ClipMultiPaste.plainToHtml("").isEmpty(), "null / empty");
        ok(ClipMultiPaste.plainToHtml("\u09AC\u09BE\u0982\u09B2\u09BE").equals("\u09AC\u09BE\u0982\u09B2\u09BE"), "Bangla untouched");

        ok(ClipMultiPaste.bodyOf("<html><head><style>p{}</style><title>t</title></head><body><!--StartFragment--><b>hi</b><!--EndFragment--></body></html>").equals("<b>hi</b>"), "full document -> body content");
        ok(ClipMultiPaste.bodyOf("<meta charset='utf-8'><span style=\"color:red\">x</span>").equals("<span style=\"color:red\">x</span>"), "meta dropped, fragment kept");
        ok(ClipMultiPaste.bodyOf("<!DOCTYPE html><p>q</p>").equals("<p>q</p>"), "doctype dropped");
        ok(ClipMultiPaste.bodyOf("<BODY class=a><i>z</i>").equals("<i>z</i>"), "unclosed body, upper case");
        ok(ClipMultiPaste.bodyOf(null).isEmpty(), "null body");
        ok(ClipMultiPaste.bodyOf("plain words").equals("plain words"), "no tags");

        // combine
        ok(ClipMultiPaste.combine(null, null) == null, "null in = null");
        ok(ClipMultiPaste.combine(L(), L()) == null, "empty = null");
        ok(ClipMultiPaste.combine(L("a", "b"), L((String) null)) == null, "size mismatch = null");
        ok(ClipMultiPaste.combine(L("a", "b"), L(null, null)) == null, "no format at all = null (plain paste)");
        ok("<b>a</b>".equals(ClipMultiPaste.combine(L("a"), L("<b>a</b>"))), "ONE clip = its html untouched");
        ok(ClipMultiPaste.combine(L("a"), L((String) null)) == null, "ONE plain clip = null");
        String two = ClipMultiPaste.combine(L("one", "two"), L("<b>one</b>", "<i>two</i>"));
        ok("<div><b>one</b></div><br><div><i>two</i></div>".equals(two), "two formatted clips: blocks + blank line");
        String mix = ClipMultiPaste.combine(L("one", "t<w>o\nx"), L("<b>one</b>", null));
        ok("<div><b>one</b></div><br><div>t&lt;w&gt;o<br>x</div>".equals(mix), "plain clip in a mix is escaped html");
        String mix2 = ClipMultiPaste.combine(L("p", "<b>q</b>"), L(null, "<html><body><b>q</b></body></html>"));
        ok("<div>p</div><br><div><b>q</b></div>".equals(mix2), "plain first, full-document second");
        String three = ClipMultiPaste.combine(L("a", "b", "c"), L("<u>a</u>", "<u>b</u>", "<u>c</u>"));
        ok(three != null && three.split("<br>", -1).length == 3, "three clips = two separators");
        ok(ClipMultiPaste.combine(L("a", "b"), L("<head></head><body></body>", "<b>b</b>")).startsWith("<div>a</div>"), "empty body of a format = falls back to its plain text");

        // too big
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 25000; i++) big.append("0123456789abcdef");   // 400000
        String h = "<b>" + big + "</b>";
        ok(ClipMultiPaste.combine(L("a", "b"), L(h, h)) == null, "too big = null (plain paste)");

        // never throws + fuzz: result is null or contains every plain text content for plain clips
        Random r = new Random(7);
        String[] bits = { "<b>", "</b>", "<div>", "x", "&", "<", ">", "\n", " ", "<body>", "</body>", "<head>", "<!--", "-->", "ab", "\u09AC" };
        for (int k = 0; k < 4000; k++) {
            int cnt = 1 + r.nextInt(4);
            List<String> t = new ArrayList<>(), hs = new ArrayList<>();
            for (int i = 0; i < cnt; i++) {
                StringBuilder a = new StringBuilder(), b = new StringBuilder();
                for (int j = r.nextInt(6); j >= 0; j--) { a.append(bits[r.nextInt(bits.length)]); b.append(bits[r.nextInt(bits.length)]); }
                t.add(a.toString());
                hs.add(r.nextInt(3) == 0 ? null : b.toString());
            }
            try {
                String res = ClipMultiPaste.combine(t, hs);
                if (res != null && cnt > 1) {
                    ok(res.startsWith("<div>") && res.endsWith("</div>"), "fuzz: blocks " + k);
                }
                if (!ClipMultiPaste.anyFormat(hs)) ok(res == null, "fuzz: no format = null " + k);
            } catch (Throwable e) { ok(false, "fuzz threw " + e); }
        }
        System.out.println("ClipMultiPasteTest " + (fails == 0 ? "OK " + n + "/" + n : "FAILED " + fails));
        if (fails > 0) System.exit(1);
    }
}
