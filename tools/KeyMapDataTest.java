import com.mgb.keyboard.phonetic.RidmikMap;
import com.mgb.keyboard.typing.KeyMapData;
import java.util.List;

/**
 * v0.16.4 (plan X, X4): the key map page data.
 * The main guard: EVERY row and EVERY example is converted by the real engine (RidmikMap.convert) and must give exactly the Bangla text
 * that the page shows. So the page can never lie. Plus: group coverage, search (roman exact / contains / Bangla), screen text, odd input.
 */
public class KeyMapDataTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }
    static String n(String s) { return KeyMapData.norm(s); }

    static boolean hasRoman(List<KeyMapData.Row> l, String roman) { for (KeyMapData.Row x : l) if (x.roman.equals(roman)) return true; return false; }

    public static void main(String[] a) {
        List<KeyMapData.Row> rows = KeyMapData.rows();
        List<KeyMapData.Example> ex = KeyMapData.examples();

        // ---- 1) the page can never lie: every row / example = the engine's own output ----
        for (KeyMapData.Row x : rows) {
            String got = RidmikMap.convert(x.roman);
            t("row " + x.roman + " -> " + x.bangla + " (engine gave " + got + ")", n(got).equals(n(x.bangla)));
        }
        for (KeyMapData.Example x : ex) {
            String got = RidmikMap.convert(x.roman);
            t("example " + x.roman + " -> " + x.bangla + " (engine gave " + got + ")", n(got).equals(n(x.bangla)));
        }

        // ---- 2) shape of the data ----
        t("rows exist (>= 80: 11 vowels + 11 kar + 38 consonants + 6 signs + 18 conjuncts)", rows.size() >= 80);
        t("examples exist (>= 30)", ex.size() >= 30);
        t("rules exist", KeyMapData.rules().size() >= 6);
        t("5 groups", KeyMapData.GROUPS.length == 5);
        for (int i = 0; i < 4; i++) t("group has rows: " + KeyMapData.GROUPS[i], KeyMapData.rowsOf(KeyMapData.GROUPS[i]).size() > 0);
        t("rules group has no table rows", KeyMapData.rowsOf(KeyMapData.G_RULES).isEmpty());
        t("unknown group = empty", KeyMapData.rowsOf("nope").isEmpty() && KeyMapData.rowsOf(null).isEmpty());
        int sum = 0; for (int i = 0; i < 4; i++) sum += KeyMapData.rowsOf(KeyMapData.GROUPS[i]).size();
        t("every row is in one of the 4 groups", sum == rows.size());
        boolean full = true;
        for (KeyMapData.Row x : rows) if (x.roman.isEmpty() || x.bangla.isEmpty() || x.group == null || x.note == null) full = false;
        for (KeyMapData.Example x : ex) if (x.roman.isEmpty() || x.bangla.isEmpty() || x.rule == null) full = false;
        t("no empty roman / bangla / null note", full);
        t("rows() is a copy", rows.size() == KeyMapData.rows().size() && KeyMapData.rows() != KeyMapData.rows());
        List<KeyMapData.Row> cp = KeyMapData.rows(); cp.clear();
        t("clearing a copy does not touch the data", KeyMapData.rows().size() == rows.size());

        // ---- 3) the key rules of the map are on the page ----
        String[] must = { "o", "a", "rri", "OI", "OU", "k", "Ng", "NG", "T", "t", "S", "Sh", "R", "TH", "ng", "HH", "qq", "cb", "hs", "kkh", "kSh", "gg", "nj", "nc" };
        boolean all = true;
        for (String m : must) if (!hasRoman(rows, m)) { all = false; System.out.println("missing roman " + m); }
        t("all key map letters are listed", all);

        // ---- 4) search ----
        t("empty query = all rows", KeyMapData.search("").size() == rows.size() && KeyMapData.search(null).size() == rows.size() && KeyMapData.search("   ").size() == rows.size());
        List<KeyMapData.Row> sNg = KeyMapData.search("Ng");
        t("search Ng: exact roman first = ঙ", !sNg.isEmpty() && sNg.get(0).roman.equals("Ng") && n(sNg.get(0).bangla).equals("ঙ"));
        List<KeyMapData.Row> sng = KeyMapData.search("ng");
        t("search ng: exact roman first = ং", !sng.isEmpty() && sng.get(0).roman.equals("ng") && n(sng.get(0).bangla).equals("ং"));
        t("search ng does not put Ng first", !sng.get(0).roman.equals("Ng"));
        List<KeyMapData.Row> sB = KeyMapData.search("ঙ");
        t("search by Bangla ঙ finds the Ng row", hasRoman(sB, "Ng"));
        t("search by Bangla ঙ does not find ক", !hasRoman(sB, "k"));
        t("search by Bangla ং finds ng", hasRoman(KeyMapData.search("ং"), "ng"));
        t("search by Bangla ক্ষ finds kkh and kSh", hasRoman(KeyMapData.search("ক্ষ"), "kkh") && hasRoman(KeyMapData.search("ক্ষ"), "kSh"));
        t("search ড় (precomposed) finds R", hasRoman(KeyMapData.search("\u09DC"), "R"));
        t("search ড় (2 characters) finds R", hasRoman(KeyMapData.search("\u09A1\u09BC"), "R"));
        t("search TH finds ৎ", KeyMapData.search("TH").get(0).roman.equals("TH"));
        t("search by note (hasanta) finds hs", hasRoman(KeyMapData.search("hasanta"), "hs"));
        t("search ref finds nothing in rows but a rule", KeyMapData.searchRules("ref").size() >= 1);
        t("no match = empty list, never null", KeyMapData.search("zzzzqx").isEmpty() && KeyMapData.searchExamples("zzzzqx").isEmpty() && KeyMapData.searchRules("zzzzqx").isEmpty());
        t("search with regex-like text does not crash and finds nothing", KeyMapData.search("\\[(*+?").isEmpty());
        t("search with only a control character = empty query after trim = all rows", KeyMapData.search("\u0000").size() == rows.size());
        t("search is case-insensitive for the contains part", hasRoman(KeyMapData.search("HASANTA"), "hs"));

        List<KeyMapData.Example> eB = KeyMapData.searchExamples("korrmo");
        t("examples: exact roman korrmo first = কর্ম", !eB.isEmpty() && n(eB.get(0).bangla).equals("কর্ম"));
        t("examples: search by Bangla বাংলা", KeyMapData.searchExamples("বাংলা").size() >= 1);
        t("examples: empty query = all", KeyMapData.searchExamples(null).size() == ex.size());
        t("rules: empty query = all", KeyMapData.searchRules("").size() == KeyMapData.rules().size());
        t("rules: Bangla query works (কর্ম)", KeyMapData.searchRules("কর্ম").size() >= 1);

        // ---- 5) screen text ----
        t("shown: normal letter unchanged", KeyMapData.shown("ক").equals("ক"));
        t("shown: ং gets a dotted circle", KeyMapData.shown("ং").equals("\u25CCং"));
        t("shown: ঁ gets a dotted circle", KeyMapData.shown("ঁ").equals("\u25CCঁ"));
        t("shown: hasanta + ZWNJ = dotted circle + hasanta (no hidden ZWNJ)", KeyMapData.shown("\u09CD\u200C").equals("\u25CC\u09CD"));
        t("shown: ZWNJ at the end of a word is removed", KeyMapData.shown("আল্লাহ্\u200C").equals("আল্লাহ্"));
        t("shown: kar sign alone gets a dotted circle", KeyMapData.shown("\u09BE").equals("\u25CC\u09BE"));
        t("shown: a letter with kar (কা) unchanged", KeyMapData.shown("কা").equals("কা"));
        t("shown: null / empty safe", KeyMapData.shown(null).equals("") && KeyMapData.shown("").equals(""));
        t("every row has a non-empty shown text", allShown(rows));
        t("norm: null stays null", KeyMapData.norm(null) == null);
        t("norm: ড় split", KeyMapData.norm("\u09DC").equals("\u09A1\u09BC"));

        System.out.println("KeyMapDataTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }

    static boolean allShown(List<KeyMapData.Row> rows) {
        for (KeyMapData.Row x : rows) if (KeyMapData.shown(x.bangla).isEmpty()) return false;
        return true;
    }
}
