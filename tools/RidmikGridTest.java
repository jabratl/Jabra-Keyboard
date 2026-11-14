import com.mgb.keyboard.layout.*;
import com.mgb.keyboard.typing.RidmikDefaults;
import java.util.*;

/**
 * v0.17.69 (S2): LOCKS the Bangla fixed (Probhat / Ridmik "prabhat") key grid, so an update can never drift from the Ridmik screenshots again.
 * Row 1 = 12 keys, row 2 = 9 keys (no extra key), row 3 = shift + 9 keys + backspace; normal AND shift letters; no "ZWNJ" key; bare signs (no dotted circle);
 * no language key by default. Change this file ONLY when the user sends a NEW Ridmik reference.
 */
public class RidmikGridTest {
    static int fail = 0, total = 0;
    static void eq(String m, Object a, Object b) { total++; if (!Objects.equals(a, b)) { fail++; System.out.println("FAIL " + m + ": " + a + " != " + b); } }

    static final String[] R1N = {"দ","ূ","ী","র","ট","এ","ু","ি","ও","প","ে","ো"};
    static final String[] R1S = {"ধ","ঊ","ঈ","ড়","ঠ","ঐ","উ","ই","ঔ","ফ","ৈ","ৌ"};
    static final String[] R2N = {"া","স","ড","ত","গ","হ","জ","ক","ল"};
    static final String[] R2S = {"অ","ষ","ঢ","থ","ঘ","ঃ","ঝ","খ","ং"};
    static final String[] R3N = {"য়","শ","চ","আ","ব","ন","ম","ৃ","্"};
    static final String[] R3S = {"য","ঢ়","ছ","ঋ","ভ","ণ","ঙ","ৃ","ঁ"};

    static List<KeyDef> chars(RowDef r) {
        List<KeyDef> l = new ArrayList<KeyDef>();
        for (KeyDef k : r.keys) if (k.kind == KeyDef.CHAR) l.add(k);
        return l;
    }

    static void row(String name, List<KeyDef> keys, String[] n, String[] s) {
        eq(name + ": key count", keys.size(), n.length);
        for (int i = 0; i < n.length && i < keys.size(); i++) {
            eq(name + " key " + (i + 1) + " normal", keys.get(i).out[0], n[i]);
            eq(name + " key " + (i + 1) + " shift", keys.get(i).shiftOut(), s[i]);
        }
    }

    public static void main(String[] a) {
        LayoutDefaults.Options o = new LayoutDefaults.Options();
        o.numRow = false;
        o.keyLang = false;   // the migrated default (RidmikDefaults)
        LayoutDef m = LayoutDefaults.build(o);
        List<RowDef> rows = m.effectiveRows("pb");
        eq("pb layer: 3 letter rows + bottom row (no number row here)", rows.size(), 4);
        row("row 1", chars(rows.get(0)), R1N, R1S);
        row("row 2", chars(rows.get(1)), R2N, R2S);
        List<KeyDef> r3 = chars(rows.get(2));
        List<KeyDef> letters3 = new ArrayList<KeyDef>();
        for (KeyDef k : r3) if (k.kind == KeyDef.CHAR && k.action.isEmpty()) letters3.add(k);
        row("row 3", letters3, R3N, R3S);
        int sp = 0; for (KeyDef k : rows.get(1).keys) if (k.kind == KeyDef.SPACER) sp++;
        eq("row 2: half-key spacer on both sides", sp, 2);
        eq("row 3: shift first", rows.get(2).keys.get(0).action, "shift");
        eq("row 3: backspace last", rows.get(2).keys.get(rows.get(2).keys.size() - 1).action, "backspace");

        boolean zwnj = false, dotted = false;
        for (RowDef r : rows) for (KeyDef k : r.keys) {
            for (String s : k.label) { if (s.contains("ZWNJ")) zwnj = true; if (s.contains("◌")) dotted = true; }
            for (String s : k.out) if (s.contains("‌")) zwnj = true;
        }
        eq("no ZWNJ key (the weird Z W key)", zwnj, false);
        eq("no dotted circle on any sign key", dotted, false);
        eq("hidden swipe-up: ৃ gives ঞ", letters3.get(7).sw[0], "ঞ");
        eq("hidden swipe-up: ্ gives ৎ", letters3.get(8).sw[0], "ৎ");
        eq("letter hint = shift letter (q)", chars(rows.get(0)).get(0).lp, "ধ");
        eq("row 3 extra keys show no hint", letters3.get(8).lp, "");
        LayoutDefaults.Options oo = new LayoutDefaults.Options(); oo.numRow = false; oo.keyLang = false;
        eq("no EN / language key when keyLang is off", LayoutDefaults.build(oo).effectiveRows("pb").get(3).keys.toString().contains("b_lang"), false);
        boolean hasLang = false;
        for (KeyDef k : m.effectiveRows("pb").get(3).keys) if ("b_lang".equals(k.id)) hasLang = true;
        eq("bottom row has no b_lang", hasLang, false);

        // original design switch keeps the old dotted look
        LayoutDefaults.Options old = new LayoutDefaults.Options(); old.numRow = false; old.pbRidmik = false;
        eq("original design: dotted circle back", LayoutDefaults.build(old).effectiveRows("pb").get(0).keys.get(1).label[0].contains("◌"), true);

        // Ridmik defaults: once, never over a stored value
        Set<String> stored = new HashSet<String>(Arrays.asList("key_lang"));
        List<Object[]> w = RidmikDefaults.toWrite(0, stored);
        boolean wl = false, we = false;
        for (Object[] d : w) { if (d[0].equals("key_lang")) wl = true; if (d[0].equals("key_emoji")) we = true; }
        eq("defaults: stored key_lang is NOT overwritten", wl, false);
        eq("defaults: key_emoji written", we, true);
        eq("defaults: applied once", RidmikDefaults.toWrite(RidmikDefaults.VERSION, new HashSet<String>()).size(), 0);

        System.out.println("RidmikGridTest: " + (total - fail) + "/" + total + (fail == 0 ? " OK" : " FAILED"));
        if (fail > 0) System.exit(1);
    }
}
