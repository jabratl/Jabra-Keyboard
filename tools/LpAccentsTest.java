import com.mgb.keyboard.layout.*;
import java.util.*;

/** v0.17.46 (P3): accent / symbol long-press options of the default layout (pure). */
public class LpAccentsTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    static KeyDef find(LayoutDef m, String id) {
        for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) if (id.equals(k.id)) return k;
        return null;
    }

    public static void main(String[] x) {
        t("u accents", LpAccents.accents('u').size() == 5 && LpAccents.accents('u').get(3).equals("\u00FC"));
        t("upper letter same", LpAccents.accents('U').equals(LpAccents.accents('u')));
        t("q none", LpAccents.accents('q').isEmpty());
        boolean small = true; for (char c = 'a'; c <= 'z'; c++) small &= LpAccents.accents(c).size() <= 8;
        t("max 8 per letter", small);
        boolean items = true; for (char c = 'a'; c <= 'z'; c++) for (String s : LpAccents.accents(c)) items &= s.length() > 0 && s.length() <= LayoutText.MAX_LP;
        t("items valid length", items);
        t("comma 8", LpAccents.comma().size() == 8 && LpAccents.period().size() == 7);

        LayoutDefaults.Options off = new LayoutDefaults.Options();
        LayoutDef a = LayoutDefaults.build(off);
        t("default off: u no list", find(a, "en_u") != null && find(a, "en_u").lpList.isEmpty());
        t("default off: comma no list", find(a, "b_comma") != null && find(a, "b_comma").lpList.isEmpty());

        LayoutDefaults.Options on = new LayoutDefaults.Options();
        on.accents = true; on.punct = true;
        LayoutDef b = LayoutDefaults.build(on);
        KeyDef u = find(b, "en_u"), comma = find(b, "b_comma"), period = find(b, "b_period"), q = find(b, "en_q");
        t("on: u has accents", u != null && u.lpList.size() == 5);
        t("on: q none", q != null && q.lpList.isEmpty());
        t("on: comma first = @emoji", comma != null && LpBarPlan.options(comma).get(0).equals("@emoji") && LpBarPlan.options(comma).size() == 9);
        t("on: period first = ?", period != null && LpBarPlan.options(period).get(0).equals("?") && LpBarPlan.options(period).size() == 8);
        t("on: u first option = old hint (when hint exists)", u != null && (u.lp.isEmpty() || LpBarPlan.options(u).get(0).equals(u.lp)));
        t("on: model passes the layout check", LayoutGuard.gateModel(b) == null);
        t("on: encode / decode keeps lists", LayoutCodec.decode(LayoutCodec.encode(b)) != null);

        System.out.println("LpAccentsTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
