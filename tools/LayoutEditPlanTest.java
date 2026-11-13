import com.mgb.keyboard.layout.*;
import com.mgb.keyboard.typing.LayoutEditPlan;

/** v0.17.8 (K6a) pure rules of the layout editor. */
public class LayoutEditPlanTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static boolean eq(float a, float b) { return Math.abs(a - b) < 0.001f; }

    public static void main(String[] a) {
        // fields
        int c = LayoutEditPlan.fields(KeyDef.CHAR);
        t("char label", LayoutEditPlan.has(c, LayoutEditPlan.F_LABEL));
        t("char out", LayoutEditPlan.has(c, LayoutEditPlan.F_OUT));
        t("char shift", LayoutEditPlan.has(c, LayoutEditPlan.F_SHIFT));
        t("char lp", LayoutEditPlan.has(c, LayoutEditPlan.F_LP));
        t("char width", LayoutEditPlan.has(c, LayoutEditPlan.F_WIDTH));
        t("action width only", LayoutEditPlan.fields(KeyDef.ACTION) == LayoutEditPlan.F_WIDTH);
        t("spacer width only", LayoutEditPlan.fields(KeyDef.SPACER) == LayoutEditPlan.F_WIDTH);
        t("unknown kind none", LayoutEditPlan.fields(9) == 0);
        // limits
        t("limit label", LayoutEditPlan.limit(LayoutEditPlan.F_LABEL) == LayoutText.MAX_LABEL);
        t("limit out", LayoutEditPlan.limit(LayoutEditPlan.F_OUT) == LayoutText.MAX_OUT);
        t("limit shift", LayoutEditPlan.limit(LayoutEditPlan.F_SHIFT) == LayoutText.MAX_OUT);
        t("limit lp", LayoutEditPlan.limit(LayoutEditPlan.F_LP) == LayoutText.MAX_LP);
        t("limit width none", LayoutEditPlan.limit(LayoutEditPlan.F_WIDTH) == 0);
        // weights
        t("plus", eq(LayoutEditPlan.nextWeight(1f, 1), 1.25f));
        t("minus", eq(LayoutEditPlan.nextWeight(1f, -1), 0.75f));
        t("zero dir", eq(LayoutEditPlan.nextWeight(1f, 0), 1f));
        t("min clamp", eq(LayoutEditPlan.nextWeight(0.25f, -1), KeyDef.W_MIN));
        t("max clamp", eq(LayoutEditPlan.nextWeight(8f, 1), KeyDef.W_MAX));
        t("nan weight", eq(LayoutEditPlan.nextWeight(Float.NaN, 1), 1.25f));
        t("inf weight", eq(LayoutEditPlan.nextWeight(Float.POSITIVE_INFINITY, -1), 0.75f));
        t("odd weight rounds", eq(LayoutEditPlan.nextWeight(1.1f, 1), LayoutOps.qw(1.35f)));
        t("text 1", LayoutEditPlan.weightText(1f).equals("1"));
        t("text 1.5", LayoutEditPlan.weightText(1.5f).equals("1.5"));
        t("text 0.25", LayoutEditPlan.weightText(0.25f).equals("0.25"));
        t("text 1.55", LayoutEditPlan.weightText(1.55f).equals("1.55"));
        t("text nan", LayoutEditPlan.weightText(Float.NaN).equals("1"));
        t("parse ok", eq(LayoutEditPlan.parseWeight("1.5"), 1.5f));
        t("parse comma", eq(LayoutEditPlan.parseWeight("2,25"), 2.25f));
        t("parse spaces", eq(LayoutEditPlan.parseWeight(" 3 "), 3f));
        t("parse low", LayoutEditPlan.parseWeight("0.1") < 0);
        t("parse high", LayoutEditPlan.parseWeight("9") < 0);
        t("parse text", LayoutEditPlan.parseWeight("abc") < 0);
        t("parse null", LayoutEditPlan.parseWeight(null) < 0);
        t("parse empty", LayoutEditPlan.parseWeight("") < 0);
        t("parse nan", LayoutEditPlan.parseWeight("NaN") < 0);
        // glyphs + shown label
        t("glyph shift", LayoutEditPlan.actionGlyph("shift").equals("\u21E7"));
        t("glyph sym", LayoutEditPlan.actionGlyph("sym").equals("?123"));
        t("glyph tab", LayoutEditPlan.actionGlyph("tab").equals("Tab"));
        t("glyph unknown", LayoutEditPlan.actionGlyph("zzz").equals("zzz"));
        t("glyph null", LayoutEditPlan.actionGlyph(null).equals(""));
        for (String act : LayoutText.ACTIONS) t("glyph not empty " + act, LayoutEditPlan.actionGlyph(act).length() > 0);
        KeyDef q = KeyDef.ch("q", "q", 1f);
        t("label normal", LayoutEditPlan.shownLabel(q, false).equals("q"));
        t("label shift", LayoutEditPlan.shownLabel(q, true).equals("Q"));
        q.label[0] = "X";
        t("custom label", LayoutEditPlan.shownLabel(q, false).equals("X"));
        t("custom label shift upper", LayoutEditPlan.shownLabel(q, true).equals("X"));
        q.label[1] = "y";
        t("label[1]", LayoutEditPlan.shownLabel(q, true).equals("y"));
        t("spacer empty", LayoutEditPlan.shownLabel(KeyDef.spacer("s", 1f), false).equals(""));
        t("action label", LayoutEditPlan.shownLabel(KeyDef.act("a", "backspace", 1f), false).equals("\u232B"));
        t("null key", LayoutEditPlan.shownLabel(null, false).equals(""));
        // model lookups on the real defaults
        LayoutDef m = LayoutDefaults.build();
        t("tabs 3", LayoutEditPlan.TAB_IDS.length == 3 && LayoutEditPlan.tabTitles().length == 3);
        t("tab id bad", LayoutEditPlan.tabId(7).equals("en"));
        for (String lay : LayoutEditPlan.TAB_IDS) {
            String f = LayoutEditPlan.firstCharId(m, lay);
            t("first char " + lay, f.length() > 0 && LayoutEditPlan.findKey(m, lay, f) != null);
            int rows = m.effectiveRows(lay).size();
            t("rows " + lay, rows >= 4);
        }
        t("find q", LayoutEditPlan.findKey(m, "en", "en_q") != null);
        t("find missing", LayoutEditPlan.findKey(m, "en", "zz") == null);
        t("find null model", LayoutEditPlan.findKey(null, "en", "en_q") == null);
        int[] p = LayoutEditPlan.locate(m, "en", "en_q");
        t("locate q", p != null && p[1] >= 0);
        t("locate missing", LayoutEditPlan.locate(m, "en", "zz") == null);
        t("describe", LayoutEditPlan.describe(m, "en", "en_q", true).startsWith("key q, row ") && LayoutEditPlan.describe(m, "en", "en_q", true).endsWith(", selected"));
        t("describe plain", !LayoutEditPlan.describe(m, "en", "en_q", false).endsWith("selected"));
        t("describe missing", LayoutEditPlan.describe(m, "en", "zz", false).equals(""));
        LayoutDef sp = LayoutOps.makeSpacer(m, "en_q");
        t("describe spacer", LayoutEditPlan.describe(sp, "en", "en_q", false).startsWith("empty spot, row "));
        // edges + hit
        RowDef r = new RowDef();
        r.keys.add(KeyDef.ch("a", "a", 1f)); r.keys.add(KeyDef.ch("b", "b", 2f)); r.keys.add(KeyDef.spacer("c", 1f));
        float[] e = LayoutEditPlan.edges(r, 400f, 0f);
        t("edges length", e.length == 6);
        t("edge first", eq(e[0], 0f) && eq(e[1], 100f));
        t("edge second", eq(e[2], 100f) && eq(e[3], 300f));
        t("edge third", eq(e[4], 300f) && eq(e[5], 400f));
        float[] g = LayoutEditPlan.edges(r, 410f, 5f);
        t("gap last right", eq(g[5], 410f));
        t("gap between", eq(g[2] - g[1], 5f));
        t("hit a", LayoutEditPlan.hit(e, 50f) == 0);
        t("hit b", LayoutEditPlan.hit(e, 250f) == 1);
        t("hit c", LayoutEditPlan.hit(e, 399f) == 2);
        t("hit gap nearest", LayoutEditPlan.hit(g, g[1] + 1f) == 0 && LayoutEditPlan.hit(g, g[2] - 1f) == 1);
        t("hit left of all", LayoutEditPlan.hit(e, -20f) == 0);
        t("hit right of all", LayoutEditPlan.hit(e, 900f) == 2);
        t("hit empty", LayoutEditPlan.hit(new float[0], 5f) == -1);
        t("hit null", LayoutEditPlan.hit(null, 5f) == -1);
        t("edges zero width", LayoutEditPlan.edges(r, 0f, 2f).length == 0);
        t("edges null row", LayoutEditPlan.edges(null, 100f, 2f).length == 0);
        t("edges tiny width", LayoutEditPlan.edges(r, 4f, 5f).length == 6);
        // rows
        float[] hs = { 40f, 40f, 20f };
        t("row 0", LayoutEditPlan.rowAt(hs, 4f, 10f) == 0);
        t("row 1", LayoutEditPlan.rowAt(hs, 4f, 50f) == 1);
        t("row 2", LayoutEditPlan.rowAt(hs, 4f, 95f) == 2);
        t("row out", LayoutEditPlan.rowAt(hs, 4f, 300f) == -1);
        t("row negative", LayoutEditPlan.rowAt(hs, 4f, -3f) == -1);
        t("row null", LayoutEditPlan.rowAt(null, 4f, 3f) == -1);
        // editing a model through the same rules the editor uses stays valid
        LayoutDef w = LayoutOps.setWeight(m, "en_q", LayoutEditPlan.nextWeight(1f, 1));
        t("weight edit valid", LayoutOps.valid(w) == null);
        LayoutDef o = LayoutOps.setOut(w, "en_q", 0, "\u0995\u09CD\u09B7");
        t("conjunct out valid", LayoutOps.valid(o) == null && LayoutEditPlan.findKey(o, "en", "en_q").out[0].equals("\u0995\u09CD\u09B7"));
        System.out.println("LayoutEditPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
