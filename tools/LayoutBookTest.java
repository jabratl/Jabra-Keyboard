import com.mgb.keyboard.layout.LayoutBook;
import com.mgb.keyboard.layout.LayoutCodec;
import com.mgb.keyboard.layout.LayoutDefaults;
import java.util.LinkedHashMap;
import java.util.Map;

/** v0.17.13 (K9) tests of layout/LayoutBook (named layouts, storage text, backup pack). */
public class LayoutBookTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + n); } }

    public static void main(String[] a) {
        String good = LayoutCodec.encode(LayoutDefaults.build());
        t("good text exists", good != null && good.length() > 100);
        LayoutBook e = LayoutBook.empty();
        t("empty size 0", e.size() == 0 && e.active().equals("") && e.names().isEmpty());
        t("clean name trims", LayoutBook.cleanName("  Work  ").equals("Work"));
        t("clean name tab / newline", LayoutBook.cleanName("a\tb\nc").equals("a b c"));
        t("clean name null", LayoutBook.cleanName(null).equals(""));
        t("clean name max 24", LayoutBook.cleanName("123456789012345678901234567890").length() == 24);
        t("clean name blank", LayoutBook.cleanName("   ").equals(""));
        // save as
        LayoutBook b = e.saveAs("Work", good);
        t("saveAs adds", b.size() == 1 && b.has("Work") && b.active().equals("Work"));
        t("textOf", b.textOf("Work").equals(good) && b.textOf("zz").equals(""));
        t("has is case-insensitive", b.has("work") && b.has("WORK") && b.indexOf("wOrK") == 0);
        LayoutBook b2 = b.saveAs("work", good + "\n");
        t("same name = replace, no new entry", b2.size() == 1 && b2.textOf("Work").equals(good + "\n") && b2.name(0).equals("Work"));
        t("saveAs blank name refused", e.saveAs("  ", good) == e);
        t("saveAs blank text refused", e.saveAs("x", "  ") == e && e.saveAs("x", null) == e);
        t("saveProblem name", e.saveProblem("", good).length() > 0);
        t("saveProblem ok", e.saveProblem("x", good).equals(""));
        t("saveProblem too big", e.saveProblem("x", new String(new char[LayoutBook.MAX_TEXT + 1]).replace('\0', 'a')).length() > 0);
        // limit 12
        LayoutBook f = e;
        for (int i = 0; i < LayoutBook.MAX_LAYOUTS; i++) f = f.saveAs("L" + i, good);
        t("12 layouts fit", f.size() == LayoutBook.MAX_LAYOUTS);
        t("13th refused", f.saveAs("extra", good) == f);
        t("13th problem text", f.saveProblem("extra", good).length() > 0);
        t("overwrite when full is fine", f.saveProblem("L3", good).equals("") && f.saveAs("L3", good + "x").size() == 12);
        t("duplicate when full refused", f.duplicate("L1") == f);
        t("add when full refused", f.add("extra", good) == f);
        // active / setActive
        LayoutBook c = e.saveAs("A", good).saveAs("B", good);
        t("active is last saved", c.active().equals("B"));
        t("setActive", c.setActive("a").active().equals("A"));
        t("setActive unknown = same", c.setActive("zz") == c);
        // add (import) keeps active
        LayoutBook d = c.add("Imp", good);
        t("add keeps active", d.size() == 3 && d.active().equals("B") && d.has("Imp"));
        t("add bad name refused", c.add(" ", good) == c);
        // rename
        LayoutBook r = c.rename("B", "Bee");
        t("rename works", r.has("Bee") && !r.has("B") && r.size() == 2);
        t("rename active follows", r.active().equals("Bee"));
        t("rename not active keeps active", c.rename("A", "Aaa").active().equals("B"));
        t("rename to existing refused", c.rename("A", "B") == c && c.rename("A", "b") == c);
        t("rename unknown refused", c.rename("zz", "q") == c);
        t("rename blank refused", c.rename("A", "  ") == c);
        t("rename case only ok", c.rename("A", "a").name(0).equals("a"));
        t("rename same name = same", c.rename("A", "A") == c);
        // duplicate
        LayoutBook du = c.duplicate("A");
        t("duplicate adds copy", du.size() == 3 && du.has("A 2") && du.textOf("A 2").equals(good));
        t("duplicate keeps active", du.active().equals("B"));
        t("duplicate twice", du.duplicate("A").has("A 3"));
        t("duplicate unknown refused", c.duplicate("zz") == c);
        String long24 = "123456789012345678901234";
        LayoutBook ln = e.saveAs(long24, good).duplicate(long24);
        t("duplicate long name stays within 24", ln.size() == 2 && LayoutBook.cleanName(ln.name(1)).equals(ln.name(1)) && ln.name(1).length() <= 24 && !ln.name(1).equals(long24));
        t("uniqueName free", e.uniqueName("My layout").equals("My layout"));
        t("uniqueName blank", e.uniqueName("").equals("Layout"));
        t("uniqueName taken", c.uniqueName("A").equals("A 2"));
        // delete
        LayoutBook dl = c.delete("B");
        t("delete active clears active", dl.size() == 1 && dl.active().equals("") && !dl.has("B"));
        t("delete other keeps active", c.delete("A").active().equals("B"));
        t("delete unknown = same", c.delete("zz") == c);
        t("delete case-insensitive", c.delete("a").size() == 1);
        // storage text
        LayoutBook rt = LayoutBook.decode(c.encode());
        t("encode / decode round trip", rt.size() == 2 && rt.active().equals("B") && rt.textOf("A").equals(good) && rt.textOf("B").equals(good));
        t("encode text escapes line breaks", c.encode().split("\n").length == 4);
        LayoutBook tricky = e.saveAs("T", "line1\nline2\r\n\t100% %25 %0A end");
        t("tricky text round trip", LayoutBook.decode(tricky.encode()).textOf("T").equals("line1\nline2\r\n\t100% %25 %0A end"));
        t("esc / unesc", LayoutBook.unesc(LayoutBook.esc("a%b\nc\td\r")).equals("a%b\nc\td\r"));
        t("unesc unknown % stays", LayoutBook.unesc("%zz%").equals("%zz%"));
        t("decode null", LayoutBook.decode(null).size() == 0);
        t("decode empty", LayoutBook.decode("").size() == 0);
        t("decode junk", LayoutBook.decode("hello\nworld").size() == 0);
        t("decode wrong header", LayoutBook.decode("BOOK9\nE\tA\tx\n").size() == 0);
        t("decode skips bad lines", LayoutBook.decode("BOOK1\nA\t\nzzz\nE\tonly\nE\tOk\tt1\n").size() == 1);
        t("decode skips duplicate names", LayoutBook.decode("BOOK1\nA\t\nE\tOk\tt1\nE\tok\tt2\n").size() == 1);
        t("decode skips uncleaned names", LayoutBook.decode("BOOK1\nA\t\nE\t  pad\tt1\n").size() == 0);
        t("decode unknown active = none", LayoutBook.decode("BOOK1\nA\tnone\nE\tOk\tt1\n").active().equals(""));
        StringBuilder many = new StringBuilder("BOOK1\nA\t\n");
        for (int i = 0; i < 30; i++) many.append("E\tN").append(i).append("\tt\n");
        t("decode enforces 12", LayoutBook.decode(many.toString()).size() == 12);
        // dropInvalid
        LayoutBook mix = e.saveAs("Good", good).saveAs("Broken", "not a layout").saveAs("Good2", good);
        LayoutBook clean = mix.dropInvalid();
        t("dropInvalid removes broken", clean.size() == 2 && !clean.has("Broken") && clean.has("Good") && clean.has("Good2"));
        t("dropInvalid keeps active when valid", clean.active().equals("Good2"));
        t("dropInvalid active broken -> empty", mix.setActive("Broken").dropInvalid().active().equals(""));
        t("dropInvalid all good = same", c.dropInvalid() == c || c.dropInvalid().size() == 2);
        // pack / unpack
        Map<String, String> kv = new LinkedHashMap<String, String>();
        kv.put("layout_v1", good); kv.put("book_v1", c.encode()); kv.put("active", "B"); kv.put("edited", "1"); kv.put("built_v", "2"); kv.put("safe_n", "2"); kv.put("good_v1", good);
        String pk = LayoutBook.pack(kv);
        Map<String, String> back = LayoutBook.unpack(pk);
        t("pack round trip", back != null && back.get("layout_v1").equals(good) && back.get("book_v1").equals(c.encode()) && back.get("active").equals("B"));
        t("pack keeps flags", back.get("edited").equals("1") && back.get("built_v").equals("2"));
        t("pack drops other keys", !back.containsKey("safe_n") && !back.containsKey("good_v1"));
        t("unpack null", LayoutBook.unpack(null) == null);
        t("unpack junk", LayoutBook.unpack("hello") == null);
        t("unpack ignores unknown keys", LayoutBook.unpack("JLP1\nevil\tx\nactive\tA\n").size() == 1);
        t("pack of empty map", LayoutBook.unpack(LayoutBook.pack(null)).isEmpty());
        t("book inside pack decodes", LayoutBook.decode(back.get("book_v1")).size() == 2);
        System.out.println("LayoutBookTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
