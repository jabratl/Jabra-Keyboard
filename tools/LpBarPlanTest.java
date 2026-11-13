import com.mgb.keyboard.layout.*;
import java.util.*;

/** v0.17.20 (L5a): long-press options bar rules (pure). */
public class LpBarPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a == null ? b == null : a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }

    static KeyDef key(String lp, String... list) {
        KeyDef k = KeyDef.ch("t1", "a", 1f);
        k.lp = lp; k.lpList = new ArrayList<String>(Arrays.asList(list));
        return k;
    }

    public static void main(String[] a) {
        eq("no options", LpBarPlan.options(key("")).size(), 0);
        eq("null key", LpBarPlan.options(null).size(), 0);
        eq("lp only", LpBarPlan.options(key("x")), Arrays.asList("x"));
        eq("lp + list", LpBarPlan.options(key("x", "y", "z")), Arrays.asList("x", "y", "z"));
        eq("duplicates dropped", LpBarPlan.options(key("x", "x", "y")), Arrays.asList("x", "y"));
        eq("empty items dropped", LpBarPlan.options(key("", "", "y", "z")), Arrays.asList("y", "z"));
        eq("max 8", LpBarPlan.options(key("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")).size(), 8);
        eq("useBar 1", LpBarPlan.useBar(key("x")), false);
        eq("useBar list of 1", LpBarPlan.useBar(key("", "x")), false);
        eq("useBar 2", LpBarPlan.useBar(key("", "x", "y")), true);
        eq("useBar lp + 1", LpBarPlan.useBar(key("x", "y")), true);
        eq("useBar null", LpBarPlan.useBar(null), false);

        eq("cell min 40dp", LpBarPlan.cell(50, 2f, 3, 1000), 80);
        eq("cell wide key", LpBarPlan.cell(120, 2f, 3, 1000), 120);
        eq("cell shrinks", LpBarPlan.cell(120, 2f, 8, 400), 50);
        eq("cell never 0", LpBarPlan.cell(0, 0f, 100, 10) >= 1, true);

        eq("left centred on key", LpBarPlan.left(100, 80, 80, 3, 1000), 100);
        eq("left clamp right", LpBarPlan.left(900, 80, 80, 3, 1000), 760);
        eq("left clamp 0", LpBarPlan.left(0, 40, 80, 3, 1000), 0);
        eq("left bigger than screen", LpBarPlan.left(10, 40, 80, 20, 1000), 0);

        eq("index first", LpBarPlan.indexAt(105f, 100, 80, 3), 0);
        eq("index mid", LpBarPlan.indexAt(200f, 100, 80, 3), 1);
        eq("index last", LpBarPlan.indexAt(330f, 100, 80, 3), 2);
        eq("index left of bar", LpBarPlan.indexAt(-50f, 100, 80, 3), 0);
        eq("index right of bar", LpBarPlan.indexAt(9999f, 100, 80, 3), 2);
        eq("index edge", LpBarPlan.indexAt(180f, 100, 80, 3), 1);
        eq("index zero count", LpBarPlan.indexAt(5f, 0, 80, 0), 0);

        eq("shown text", LpBarPlan.shown("x"), "x");
        eq("shown action", LpBarPlan.shown("@paste"), "paste");
        eq("shown at alone", LpBarPlan.shown("@"), "@");
        eq("shown null", LpBarPlan.shown(null), "");

        eq("parse basic", LpBarPlan.parse("a b  c"), Arrays.asList("a", "b", "c"));
        eq("parse empty", LpBarPlan.parse("   ").size(), 0);
        eq("parse null", LpBarPlan.parse(null).size(), 0);
        eq("parse unknown action dropped", LpBarPlan.parse("a @nope @tab"), Arrays.asList("a", "@tab"));
        eq("parse bangla conjunct kept", LpBarPlan.parse("\u0995\u09CD\u09B7 \u0983").get(0), "\u0995\u09CD\u09B7");
        eq("parse max 8", LpBarPlan.parse("1 2 3 4 5 6 7 8 9 10").size(), 8);
        eq("parse cuts long item", LpBarPlan.parse("abcdefghijklmnopqrstuvwxyz").get(0).length(), 16);
        eq("join", LpBarPlan.join(Arrays.asList("a", "b")), "a b");
        eq("join null", LpBarPlan.join(null), "");

        // v0.17.44 (P1): 2-row grid. 8 options = 2 rows x 4; option 0..3 bottom row (under the key), 4..7 top row.
        eq("rows 5", LpBarPlan.rows(5), 1);
        eq("rows 6", LpBarPlan.rows(6), 2);
        eq("perRow 8", LpBarPlan.perRow(8), 4);
        eq("perRow 7", LpBarPlan.perRow(7), 4);
        eq("perRow 3 single", LpBarPlan.perRow(3), 3);
        eq("grid bottom-left = first", LpBarPlan.indexAtGrid(105f, 500f, 100, 400, 80, 50, 4, 8), 0);
        eq("grid bottom col 2", LpBarPlan.indexAtGrid(270f, 460f, 100, 400, 80, 50, 4, 8), 2);
        eq("grid top row col 0", LpBarPlan.indexAtGrid(105f, 410f, 100, 400, 80, 50, 4, 8), 4);
        eq("grid top row col 3", LpBarPlan.indexAtGrid(9999f, 380f, 100, 400, 80, 50, 4, 8), 7);
        eq("grid finger on key (below) = bottom row", LpBarPlan.indexAtGrid(105f, 900f, 100, 400, 80, 50, 4, 8), 0);
        eq("grid short top row clamps to last", LpBarPlan.indexAtGrid(9999f, 410f, 100, 400, 80, 50, 4, 7), 6);
        eq("grid zero count", LpBarPlan.indexAtGrid(5f, 5f, 0, 0, 80, 50, 4, 0), 0);
        eq("round trip", LpBarPlan.parse(LpBarPlan.join(Arrays.asList("\u0995", "@tab", "x"))), Arrays.asList("\u0995", "@tab", "x"));

        // model round trip through ops + codec
        LayoutDef d = LayoutDefaults.build();
        LayoutDef e = LayoutOps.setLpList(d, "en_q", LpBarPlan.parse("1 2 3"));
        eq("setLpList changes", e != d, true);
        eq("valid", LayoutOps.valid(e), null);
        LayoutDef r = LayoutCodec.decode(LayoutCodec.encode(e));
        eq("codec round trip", r != null && r.sig().equals(e.sig()), true);
        KeyDef q = null;
        for (LayerDef l : r.layers) for (RowDef rw : l.rows) for (KeyDef k : rw.keys) if (k.id.equals("en_q")) q = k;
        eq("options after codec", LpBarPlan.options(q).size() >= 3, true);

        System.out.println("LpBarPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
