import com.mgb.keyboard.layout.KeyDef;
import com.mgb.keyboard.layout.LayerDef;
import com.mgb.keyboard.layout.LayoutCodec;
import com.mgb.keyboard.layout.LayoutDef;
import com.mgb.keyboard.layout.LayoutDefaults;
import com.mgb.keyboard.layout.LayoutDrag;
import com.mgb.keyboard.layout.LayoutOps;
import com.mgb.keyboard.layout.RowDef;

/** v0.17.16 (K11a) tests of layout/LayoutDrag (drop zones, swap / insert, refusals, auto-scroll). */
public class LayoutDragTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + n); } }

    static String ids(RowDef r) { StringBuilder b = new StringBuilder(); for (KeyDef k : r.keys) b.append(k.id).append(' '); return b.toString().trim(); }

    static RowDef rowOf(LayoutDef m, String layer, int i) { return m.layer(layer).rows.get(i); }

    public static void main(String[] a) {
        LayoutDef d = LayoutDefaults.build();
        int rq = 1;   // row 1 of en (row 0 is the number row reference)
        RowDef r0 = rowOf(d, "en", rq);
        t("row 1 is q w e ...", ids(r0).startsWith("en_q en_w en_e"));
        // zones
        t("zone before", LayoutDrag.zone(0, 100, 10) == LayoutDrag.BEFORE);
        t("zone after", LayoutDrag.zone(0, 100, 90) == LayoutDrag.AFTER);
        t("zone swap", LayoutDrag.zone(0, 100, 50) == LayoutDrag.SWAP);
        t("zone edge 25 is swap", LayoutDrag.zone(0, 100, 25) == LayoutDrag.SWAP && LayoutDrag.zone(0, 100, 75) == LayoutDrag.SWAP);
        t("zone zero width", LayoutDrag.zone(5, 5, 5) == LayoutDrag.SWAP);
        // swap in a row
        LayoutDef s = LayoutDrag.drop(d, "en_q", "en_e", LayoutDrag.SWAP);
        t("swap changes", s != d && ids(rowOf(s, "en", rq)).startsWith("en_e en_w en_q"));
        t("swap keeps key count", s.totalKeys() == d.totalKeys());
        t("swap result valid", LayoutOps.valid(s) == null && LayoutCodec.encode(s) != null);
        t("swap again = back", LayoutDrag.drop(s, "en_q", "en_e", LayoutDrag.SWAP).equals(d));
        t("source model untouched", ids(rowOf(d, "en", rq)).startsWith("en_q en_w en_e"));
        // insert
        LayoutDef b = LayoutDrag.drop(d, "en_q", "en_e", LayoutDrag.AFTER);
        t("insert after", ids(rowOf(b, "en", rq)).startsWith("en_w en_e en_q en_r"));
        LayoutDef c = LayoutDrag.drop(d, "en_e", "en_q", LayoutDrag.BEFORE);
        t("insert before", ids(rowOf(c, "en", rq)).startsWith("en_e en_q en_w en_r"));
        t("insert keeps weight", findKey(b, "en_q").weight == findKey(d, "en_q").weight);
        t("insert before the next key = no change refused", LayoutDrag.drop(d, "en_q", "en_w", LayoutDrag.BEFORE) == d);
        t("insert after the previous key = no change refused", LayoutDrag.drop(d, "en_w", "en_q", LayoutDrag.AFTER) == d);
        // across rows of one layer
        LayoutDef x = LayoutDrag.drop(d, "en_q", "en_a", LayoutDrag.SWAP);
        t("swap across rows", ids(rowOf(x, "en", rq)).startsWith("en_a") && ids(rowOf(x, "en", rq + 1)).contains("en_q"));
        LayoutDef y = LayoutDrag.drop(d, "en_q", "en_a", LayoutDrag.BEFORE);
        t("insert across rows", y != d && ids(rowOf(y, "en", rq + 1)).startsWith("en_gl en_q en_a") || ids(rowOf(y, "en", rq + 1)).contains("en_q en_a"));
        t("insert across rows: source row shorter", rowOf(y, "en", rq).keys.size() == rowOf(d, "en", rq).keys.size() - 1);
        t("across rows valid", LayoutOps.valid(y) == null);
        // refusals
        t("same key", LayoutDrag.drop(d, "en_q", "en_q", LayoutDrag.SWAP) == d);
        t("unknown source", LayoutDrag.drop(d, "nope", "en_q", LayoutDrag.SWAP) == d);
        t("unknown target", LayoutDrag.drop(d, "en_q", "nope", LayoutDrag.SWAP) == d);
        t("null model", LayoutDrag.drop(null, "a", "b", LayoutDrag.SWAP) == null);
        t("null ids", LayoutDrag.drop(d, null, "en_q", LayoutDrag.SWAP) == d && LayoutDrag.drop(d, "en_q", null, LayoutDrag.SWAP) == d);
        t("bad mode", LayoutDrag.drop(d, "en_q", "en_w", 9) == d);
        t("letter into the shared bottom row refused", LayoutDrag.drop(d, "en_q", "b_comma", LayoutDrag.SWAP) == d && LayoutDrag.drop(d, "en_q", "b_comma", LayoutDrag.BEFORE) == d);
        t("letter into the shared number row refused", LayoutDrag.drop(d, "en_q", "nr_1", LayoutDrag.AFTER) == d);
        t("bottom row keys can swap inside their row", LayoutDrag.drop(d, "b_comma", "b_period", LayoutDrag.SWAP) != d);
        t("symbol page key to another page refused", LayoutDrag.drop(d, "s1_r1_1", "s2_r1_1", LayoutDrag.SWAP) == d);
        t("symbol keys inside one page ok", LayoutDrag.drop(d, "s1_r1_1", "s1_r2_1", LayoutDrag.SWAP) != d);
        // single key row can be swapped but not moved out
        LayoutDef one = d.copy();
        RowDef lone = new RowDef(); lone.keys.add(KeyDef.ch("lone1", "z", 1f));
        one.layer("s1").rows.add(lone);
        LayoutDef one2 = LayoutOps.normalize(one);
        t("lone row prepared", one2 != null && LayoutOps.valid(one2) == null);
        t("moving the only key of a row out is refused", LayoutDrag.drop(one2, "lone1", "s1_r1_1", LayoutDrag.BEFORE) == one2);
        t("swapping the only key is fine", LayoutDrag.drop(one2, "lone1", "s1_r1_1", LayoutDrag.SWAP) != one2);
        // spacer target
        LayoutDef sp = LayoutOps.makeSpacer(d, "en_w");
        LayoutDef sp2 = LayoutDrag.drop(sp, "en_q", "en_w", LayoutDrag.SWAP);
        t("swap with an empty spot", sp2 != sp && findKey(sp2, "en_w").kind == KeyDef.SPACER && rowOf(sp2, "en", 1).keys.get(0).id.equals("en_w") && rowOf(sp2, "en", 1).keys.get(1).id.equals("en_q"));
        // canDrop
        t("canDrop true / false", LayoutDrag.canDrop(d, "en_q", "en_e", LayoutDrag.SWAP) && !LayoutDrag.canDrop(d, "en_q", "b_comma", LayoutDrag.SWAP) && !LayoutDrag.canDrop(null, "a", "b", 0));
        t("labels", LayoutDrag.label(LayoutDrag.BEFORE).length() > 3 && LayoutDrag.label(LayoutDrag.AFTER).length() > 3 && LayoutDrag.label(LayoutDrag.SWAP).length() > 3);
        // every key of en can be swapped with its neighbour and stays valid
        boolean all = true;
        for (RowDef r : d.layer("en").rows) {
            if (r.isRef()) continue;
            for (int i = 0; i + 1 < r.keys.size(); i++) {
                LayoutDef q = LayoutDrag.drop(d, r.keys.get(i).id, r.keys.get(i + 1).id, LayoutDrag.SWAP);
                if (q == d || LayoutOps.valid(q) != null || q.totalKeys() != d.totalKeys()) all = false;
            }
        }
        t("neighbour swaps all valid", all);
        // auto scroll
        t("scroll none in the middle", LayoutDrag.autoScrollDy(500, 100, 900, 80, 20) == 0);
        t("scroll up near the top", LayoutDrag.autoScrollDy(110, 100, 900, 80, 20) < 0);
        t("scroll down near the bottom", LayoutDrag.autoScrollDy(890, 100, 900, 80, 20) > 0);
        t("scroll faster at the edge", LayoutDrag.autoScrollDy(100, 100, 900, 80, 20) <= LayoutDrag.autoScrollDy(150, 100, 900, 80, 20));
        t("scroll max step", Math.abs(LayoutDrag.autoScrollDy(-500, 100, 900, 80, 20)) == 20 && LayoutDrag.autoScrollDy(5000, 100, 900, 80, 20) == 20);
        t("scroll bad input", LayoutDrag.autoScrollDy(1, 5, 5, 80, 20) == 0 && LayoutDrag.autoScrollDy(1, 0, 10, 0, 20) == 0 && LayoutDrag.autoScrollDy(1, 0, 10, 5, 0) == 0);
        System.out.println("LayoutDragTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }

    static KeyDef findKey(LayoutDef m, String id) {
        for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) if (k.id.equals(id)) return k;
        return null;
    }
}
