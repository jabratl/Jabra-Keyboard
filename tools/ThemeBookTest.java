import com.mgb.keyboard.typing.BackupCodec;
import com.mgb.keyboard.typing.CustomTheme;
import com.mgb.keyboard.typing.ThemeBook;
import java.util.LinkedHashMap;
import java.util.Map;

/** v0.17.24 (L4) tests of typing/ThemeBook (named custom themes, storage text, limits). */
public class ThemeBookTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + n); } }

    static String th(int i) { CustomTheme c = CustomTheme.preset(i); return c.toJson(); }

    public static void main(String[] a) {
        String j0 = th(0), j1 = th(1), j2 = th(2);
        ThemeBook e = ThemeBook.empty();
        t("empty", e.size() == 0 && e.active().equals("") && e.names().isEmpty());
        t("clean name", ThemeBook.cleanName("  Night\tgold ").equals("Night gold") && ThemeBook.cleanName(null).equals(""));
        // saveAs
        ThemeBook b = e.saveAs("Night", j0);
        t("saveAs adds + active", b.size() == 1 && b.has("night") && b.active().equals("Night"));
        t("jsonOf", b.jsonOf("NIGHT").equals(j0) && b.jsonOf("zz").equals(""));
        t("empty untouched (immutable)", e.size() == 0);
        ThemeBook b2 = b.saveAs("night", j1);
        t("same name replaces, keeps stored case", b2.size() == 1 && b2.jsonOf("Night").equals(j1) && b2.active().equals("Night"));
        t("sameAs", b2.sameAs("Night", j1) && !b2.sameAs("Night", j0) && !b2.sameAs("x", j1) && !b2.sameAs("Night", null));
        // refusals return the SAME object
        t("blank name refused", b.saveAs("  ", j0) == b && b.saveProblem(" ", j0).equals("type a name"));
        t("null json refused", b.saveAs("X", null) == b && b.saveProblem("X", null).length() > 0);
        t("garbage json refused", b.saveAs("X", "hello") == b && b.saveProblem("X", "{\"ver\":2}").length() > 0);
        String big = j0.substring(0, j0.length() - 1) + new String(new char[2100]).replace('\0', ' ') + "}";
        t("too big refused", CustomTheme.parse(big) != null && b.saveAs("X", big) == b && b.saveProblem("X", big).equals("theme too big"));
        t("add keeps active", b.add("Y", j1).active().equals("Night") && b.add("Y", j1).size() == 2 && b.add(" ", j1) == b);
        // limit
        ThemeBook f = ThemeBook.empty();
        for (int i = 0; i < ThemeBook.MAX_THEMES; i++) f = f.saveAs("T" + i, j0);
        t("limit 12 reached", f.size() == 12);
        t("13th refused", f.saveAs("Extra", j0) == f && f.saveProblem("Extra", j0).contains("12"));
        t("overwrite allowed when full", f.saveProblem("T3", j1).equals("") && f.saveAs("T3", j1).size() == 12);
        t("duplicate refused when full", f.duplicate("T1") == f);
        // unique name
        ThemeBook u = e.saveAs("Dark", j0).saveAs("Dark 2", j0);
        t("uniqueName", u.uniqueName("Dark").equals("Dark 3") && u.uniqueName("Free").equals("Free") && u.uniqueName("").equals("Theme"));
        t("uniqueName long stays within 24", u.saveAs("123456789012345678901234", j0).uniqueName("123456789012345678901234").length() <= 24);
        // setActive / clearActive
        ThemeBook c = e.saveAs("A", j0).saveAs("B", j1);
        t("active = last saved", c.active().equals("B"));
        t("setActive", c.setActive("a").active().equals("A") && c.setActive("zz") == c);
        t("clearActive", c.clearActive().active().equals("") && c.clearActive().clearActive().size() == 2 && e.clearActive() == e);
        // rename
        ThemeBook r = c.rename("B", "Bee");
        t("rename follows active", r.has("Bee") && !r.has("B") && r.active().equals("Bee") && r.jsonOf("Bee").equals(j1));
        t("rename to taken refused", c.rename("B", "a") == c);
        t("rename case only ok", c.rename("B", "b").name(1).equals("b"));
        t("rename unknown / blank refused", c.rename("zz", "x") == c && c.rename("B", " ") == c);
        // duplicate
        ThemeBook d = c.duplicate("A");
        t("duplicate copy not active", d.size() == 3 && d.has("A 2") && d.active().equals("B") && d.jsonOf("A 2").equals(j0));
        t("duplicate unknown refused", c.duplicate("zz") == c);
        // delete
        ThemeBook x = c.delete("B");
        t("delete active clears active", x.size() == 1 && x.active().equals("") && !x.has("B"));
        t("delete other keeps active", c.delete("A").active().equals("B") && c.delete("zz") == c);
        // storage round trip
        ThemeBook s = e.saveAs("Night\tgold", j0).saveAs("Rose 100%", j2).saveAs("Café", j1).setActive("Rose 100%");
        String raw = s.encode();
        ThemeBook back = ThemeBook.decode(raw);
        t("encode head", raw.startsWith("TBOOK1\n"));
        t("round trip names / json / active", back.size() == s.size() && back.active().equals(s.active()) && back.jsonOf("Rose 100%").equals(j2) && back.jsonOf("Café").equals(j1) && back.has("Night gold"));
        t("round trip encode equal", back.encode().equals(raw));
        // decode safety
        t("decode null / empty / junk", ThemeBook.decode(null).size() == 0 && ThemeBook.decode("").size() == 0 && ThemeBook.decode("hello").size() == 0 && ThemeBook.decode("BOOK1\nE\ta\tb").size() == 0);
        String bad1 = "TBOOK1\nA\tX\nE\tOk\t" + j0 + "\nE\tBroken\t{nope}\nE\tNoTab\nE\t \t" + j0 + "\nE\tOk\t" + j1 + "\nzzz\n";
        ThemeBook p = ThemeBook.decode(bad1);
        t("decode skips bad lines / duplicate names", p.size() == 1 && p.has("Ok") && p.jsonOf("Ok").equals(j0));
        t("decode unknown active -> none", p.active().equals(""));
        StringBuilder many = new StringBuilder("TBOOK1\n");
        for (int i = 0; i < 30; i++) many.append("E\tN").append(i).append('\t').append(j0).append('\n');
        t("decode enforces limit", ThemeBook.decode(many.toString()).size() == ThemeBook.MAX_THEMES);
        // dropInvalid (restore gate)
        t("dropInvalid keeps a good book as is", s.dropInvalid() == s);
        t("every saved theme parses", CustomTheme.parse(s.jsonOf("Café")) != null);
        // the book is a normal pref (themes_v1): the existing backup carries it unchanged
        Map<String, Object> pf = new LinkedHashMap<String, Object>(); pf.put("themes_v1", s.encode());
        String bk = BackupCodec.build(pf, new LinkedHashMap<String, String>(), "x", 1L);
        Object got = BackupCodec.prefs(bk).get("themes_v1");
        t("backup round trip of the pref text", BackupCodec.validate(bk) == BackupCodec.OK && s.encode().equals(got) && ThemeBook.decode((String) got).size() == 3);
        System.out.println("ThemeBookTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
