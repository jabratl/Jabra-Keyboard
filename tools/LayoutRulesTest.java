import com.mgb.keyboard.layout.*;
import java.util.*;

/** v0.17.17 (K13) layout rules: resolve priority, limits, storage text, dropMissing, backup pack key. */
public class LayoutRulesTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        LayoutRules e = LayoutRules.empty();
        t("empty", e.isEmpty() && e.size() == 0 && e.resolve("com.x", "en", false).equals(""));
        t("valid kinds", LayoutRules.validKind("lang") && LayoutRules.validKind("orient") && LayoutRules.validKind("app") && !LayoutRules.validKind("x") && !LayoutRules.validKind(null));
        t("valid lang keys", LayoutRules.validKey("lang", "en") && LayoutRules.validKey("lang", "av") && LayoutRules.validKey("lang", "pb") && !LayoutRules.validKey("lang", "s1") && !LayoutRules.validKey("lang", "EN"));
        t("valid orient keys", LayoutRules.validKey("orient", "p") && LayoutRules.validKey("orient", "l") && !LayoutRules.validKey("orient", "x"));
        t("valid app keys", LayoutRules.validKey("app", "com.termux") && !LayoutRules.validKey("app", "bad pkg") && !LayoutRules.validKey("app", "") && !LayoutRules.validKey("app", null) && !LayoutRules.validKey(null, "en"));

        LayoutRules r = e.put("lang", "pb", "Bangla fast");
        t("put lang", r != e && r.size() == 1 && r.nameFor("lang", "pb").equals("Bangla fast"));
        t("put refuses bad key", e.put("lang", "zz", "A") == e);
        t("put refuses empty name", e.put("lang", "en", "  ") == e && e.put("lang", "en", null) == e);
        t("put refuses bad kind", e.put("zzz", "en", "A") == e);
        t("immutable", e.size() == 0 && r.size() == 1);
        LayoutRules r2 = r.put("lang", "pb", "Other");
        t("same kind+key replaces", r2.size() == 1 && r2.nameFor("lang", "pb").equals("Other"));
        t("name cleaned", e.put("lang", "en", "  Hi\tthere \n").nameFor("lang", "en").equals("Hi there"));

        // priority: app > lang > orient
        LayoutRules p = LayoutRules.empty().put("orient", "l", "Wide").put("lang", "en", "Plain").put("app", "com.termux", "Coder");
        t("app wins", p.resolve("com.termux", "en", true).equals("Coder"));
        t("lang beats orient", p.resolve("com.other", "en", true).equals("Plain"));
        t("orient when no lang rule", p.resolve("com.other", "pb", true).equals("Wide"));
        t("nothing matches (portrait)", p.resolve("com.other", "pb", false).equals(""));
        t("null package", p.resolve(null, "en", false).equals("Plain"));
        t("null layer", p.resolve("com.other", null, true).equals("Wide"));
        t("empty package", p.resolve("", "pb", true).equals("Wide"));

        // remove / clear
        t("remove one", p.remove("app", "com.termux").size() == 2 && p.remove("app", "com.termux").nameFor("app", "com.termux").equals(""));
        t("remove unknown = same", p.remove("app", "com.nope") == p);
        t("clear", p.clear().isEmpty() && e.clear() == e);

        // limit
        LayoutRules big = LayoutRules.empty();
        for (int i = 0; i < LayoutRules.MAX_RULES; i++) big = big.put("app", "com.app" + i, "L");
        t("limit reached", big.size() == LayoutRules.MAX_RULES);
        t("41st refused", big.put("app", "com.extra", "L") == big);
        t("replace at the limit is fine", big.put("app", "com.app3", "M") != big && big.put("app", "com.app3", "M").size() == LayoutRules.MAX_RULES);

        // dropMissing
        LayoutBook b = LayoutBook.empty();
        String good = LayoutCodec.encode(LayoutDefaults.build());
        b = b.add("Coder", good).add("Wide", good);
        LayoutRules d = LayoutRules.empty().put("app", "com.termux", "coder").put("orient", "l", "Wide").put("lang", "pb", "Gone");
        LayoutRules dm = d.dropMissing(b);
        t("dropMissing removes the unknown name", dm.size() == 2 && dm.nameFor("lang", "pb").equals(""));
        t("dropMissing uses the book spelling", dm.nameFor("app", "com.termux").equals("Coder"));
        LayoutRules fine = LayoutRules.empty().put("orient", "l", "Wide");
        t("dropMissing nothing = same", fine.dropMissing(b) == fine);
        t("dropMissing null book = empty", d.dropMissing(null).isEmpty());

        // changed
        t("changed same", !LayoutRules.changed("A", "A") && !LayoutRules.changed(null, "") && !LayoutRules.changed("", null));
        t("changed differs", LayoutRules.changed("A", "B") && LayoutRules.changed("", "B") && LayoutRules.changed("A", ""));

        // texts
        t("keyTitle", LayoutRules.keyTitle("lang", "en").equals("English") && LayoutRules.keyTitle("lang", "av").equals("Bangla (Avro)") && LayoutRules.keyTitle("lang", "pb").equals("Bangla (Probhat)"));
        t("keyTitle orient app", LayoutRules.keyTitle("orient", "l").equals("Landscape") && LayoutRules.keyTitle("orient", "p").equals("Portrait") && LayoutRules.keyTitle("app", "com.x").equals("com.x"));
        t("line", p.line(0).length() > 0 && p.line(99).equals("") && p.line(-1).equals(""));
        t("line shape", LayoutRules.empty().put("lang", "en", "Coder").line(0).equals("Language English -> Coder"));

        // storage round trip
        LayoutRules z = LayoutRules.empty().put("app", "com.termux", "Co%der").put("lang", "pb", "\u09AC\u09BE\u0982\u09B2\u09BE").put("orient", "l", "Wide");
        String txt = z.encode();
        t("encode header", txt.startsWith("RULES1\n"));
        LayoutRules z2 = LayoutRules.decode(txt);
        t("round trip", z2.size() == 3 && z2.encode().equals(txt));
        t("round trip values", z2.nameFor("app", "com.termux").equals("Co%der") && z2.nameFor("lang", "pb").equals("\u09AC\u09BE\u0982\u09B2\u09BE"));
        t("decode null / empty / junk", LayoutRules.decode(null).isEmpty() && LayoutRules.decode("").isEmpty() && LayoutRules.decode("hello").isEmpty() && LayoutRules.decode("RULES9\nR\tlang\ten\tA\n").isEmpty());
        t("decode skips bad lines", LayoutRules.decode("RULES1\nR\tlang\tzz\tA\nR\tlang\ten\tB\nX\tlang\tav\tC\nR\tlang\n").size() == 1);
        t("decode skips duplicates (first wins)", LayoutRules.decode("RULES1\nR\tlang\ten\tA\nR\tlang\ten\tB\n").nameFor("lang", "en").equals("A"));
        t("decode skips uncleaned names", LayoutRules.decode("RULES1\nR\tlang\ten\t Spaced \n").isEmpty());
        StringBuilder many = new StringBuilder("RULES1\n");
        for (int i = 0; i < 100; i++) many.append("R\tapp\tcom.a").append(i).append("\tL\n");
        t("decode limits to MAX_RULES", LayoutRules.decode(many.toString()).size() == LayoutRules.MAX_RULES);
        t("decode huge text refused", LayoutRules.decode("RULES1\n" + new String(new char[30000]).replace('\0', 'x')).isEmpty());

        // fuzz: never throws
        Random rnd = new Random(13);
        boolean allOk = true;
        String alpha = "RULES1\n\t%AB lang app orient en pb p l com.x";
        for (int n = 0; n < 2000; n++) {
            StringBuilder sb = new StringBuilder();
            int len = rnd.nextInt(80);
            for (int i = 0; i < len; i++) sb.append(alpha.charAt(rnd.nextInt(alpha.length())));
            try { LayoutRules q = LayoutRules.decode(sb.toString()); q.encode(); q.resolve("com.x", "en", true); } catch (Throwable ex) { allOk = false; }
        }
        t("fuzz never throws", allOk);

        // backup pack carries the rules key
        t("PACK_KEYS has rules_v1", Arrays.asList(LayoutBook.PACK_KEYS).contains("rules_v1"));
        Map<String, String> kv = new LinkedHashMap<String, String>();
        kv.put("rules_v1", txt);
        Map<String, String> back = LayoutBook.unpack(LayoutBook.pack(kv));
        t("pack round trip of the rules", back != null && txt.equals(back.get("rules_v1")));

        System.out.println("LayoutRulesTest: " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
