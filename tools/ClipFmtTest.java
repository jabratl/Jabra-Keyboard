import com.mgb.keyboard.clip.ClipFmt;
import java.util.*;

public class ClipFmtTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static String rep(char c, int n) { char[] a = new char[n]; Arrays.fill(a, c); return new String(a); }
    public static void main(String[] x) {
        ClipFmt f = new ClipFmt();
        t("empty", f.size() == 0 && f.get("a") == null && f.serialize().equals(""));
        t("put A", f.put("A", "<b>A</b>") && "<b>A</b>".equals(f.get("A")) && f.size() == 1);
        t("put B C", f.put("B", "<i>B</i>") && f.put("C", "<u>C</u>") && f.size() == 3);
        t("each keeps its own", "<b>A</b>".equals(f.get("A")) && "<i>B</i>".equals(f.get("B")) && "<u>C</u>".equals(f.get("C")));
        t("same html again = no change", !f.put("A", "<b>A</b>"));
        t("newer format replaces", f.put("A", "<h1>A</h1>") && "<h1>A</h1>".equals(f.get("A")) && f.size() == 3);
        t("plain copy (null) removes", f.put("B", null) && f.get("B") == null && f.size() == 2);
        t("blank html removes", f.put("C", "  ") && f.get("C") == null && f.size() == 1);
        t("null key / empty key ignored", !f.put(null, "<b>x</b>") && !f.put("", "<b>x</b>") && f.size() == 1);
        t("remove missing = false", !f.remove("zzz") && !f.remove(null));
        t("remove", f.remove("A") && f.size() == 0 && f.totalChars() == 0);
        // caps
        ClipFmt g = new ClipFmt();
        t("100000 ok", g.put("k", rep('a', ClipFmt.MAX_ONE)) && g.has("k"));
        t("100001 dropped", !g.put("big", rep('a', ClipFmt.MAX_ONE + 1)) && !g.has("big"));
        t("200 KB html dropped, old format of the same key goes", g.put("k", rep('b', 200000)) && !g.has("k"));
        ClipFmt h = new ClipFmt();
        for (int i = 0; i < 20; i++) h.put("key" + i, rep('x', 99000));
        t("total cap", h.totalChars() <= ClipFmt.MAX_TOTAL);
        t("oldest dropped first", !h.has("key0") && h.has("key19"));
        t("count after cap", h.size() >= 14 && h.size() <= 15);
        // prune
        ClipFmt p = new ClipFmt(); p.put("a", "<a/>"); p.put("b", "<b/>"); p.put("c", "<c/>");
        t("prune keeps listed", p.prune(Arrays.asList("a", "c", "zz")) && p.has("a") && !p.has("b") && p.has("c"));
        t("prune nothing = false", !p.prune(Arrays.asList("a", "c")));
        t("prune total ok", p.totalChars() == ("a".length() + 4) + ("c".length() + 4));
        t("prune null = all", p.prune(null) && p.size() == 0 && p.totalChars() == 0);
        // serialize roundtrip
        ClipFmt s = new ClipFmt();
        s.put("line1\nline2\ttab \\ back", "<p>a\r\nb\tc \\n literal \\ end</p>");
        s.put("বাংলা টেক্সট", "<span style=\"color:red\">বাংলা</span>");
        s.put("plain", "<b>x</b>");
        String ser = s.serialize();
        t("one line per entry", ser.split("\n", -1).length == 3);
        ClipFmt r = ClipFmt.parse(ser);
        t("roundtrip size", r.size() == 3 && r.totalChars() == s.totalChars());
        t("roundtrip tricky", "<p>a\r\nb\tc \\n literal \\ end</p>".equals(r.get("line1\nline2\ttab \\ back")));
        t("roundtrip bangla", "<span style=\"color:red\">বাংলা</span>".equals(r.get("বাংলা টেক্সট")));
        t("roundtrip serialize equal", r.serialize().equals(ser));
        t("order kept (oldest first)", ser.indexOf("plain") > ser.indexOf("line1"));
        // broken input
        t("parse null", ClipFmt.parse(null).size() == 0);
        t("parse empty", ClipFmt.parse("").size() == 0);
        t("parse garbage lines skipped", ClipFmt.parse("no tab here\n\n\tnokey\nkey\t\nok\t<b>1</b>\ntrailing\\").size() == 1);
        t("parse keeps good line", "<b>1</b>".equals(ClipFmt.parse("junk\nok\t<b>1</b>").get("ok")));
        t("parse applies cap", ClipFmt.parse("k\t" + rep('z', 150000)).size() == 0);
        t("clear", p.put("q", "<q/>") && sizeAfterClear(p) == 0);
        System.out.println("ClipFmtTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
    static int sizeAfterClear(ClipFmt f) { f.clear(); return f.size() + (int) f.totalChars(); }
}
