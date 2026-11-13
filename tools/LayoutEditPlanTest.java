import com.mgb.keyboard.layout.*;
import com.mgb.keyboard.typing.LayoutEditPlan;

/** v0.17.8 (K6a) pure rules of the layout editor. */
public class LayoutEditPlanTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static boolean eq(float a, float b) { return Math.abs(a - b) < 0.001f; }

    static boolean allNamed() { for (String a : LayoutText.AT_ACTIONS) if (LayoutEditPlan.atName(a).equals(a)) return false; return true; }
    static boolean sortedOk(java.util.List<LayoutCheck.Warn> l) { for (int i = 1; i < l.size(); i++) if (l.get(i - 1).severity < l.get(i).severity) return false; return true; }
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
        // ---- v0.17.9 (K6b) structure helpers ----
        t("height up", LayoutEditPlan.nextHeight(100, 1) == 110);
        t("height down", LayoutEditPlan.nextHeight(100, -1) == 90);
        t("height min", LayoutEditPlan.nextHeight(50, -1) == 50);
        t("height max", LayoutEditPlan.nextHeight(200, 1) == 200);
        t("height zero dir", LayoutEditPlan.nextHeight(100, 0) == 100);
        t("height wild low", LayoutEditPlan.nextHeight(-40, 0) == 50);
        String[] ow = LayoutEditPlan.owner(m, "en_q");
        t("owner q", ow != null && ow[0].equals("en"));
        String nrId = LayoutEditPlan.firstCharId(m, "en");
        t("owner null id", LayoutEditPlan.owner(m, null) == null && LayoutEditPlan.owner(null, "en_q") == null && LayoutEditPlan.owner(m, "nope") == null);
        String[] ob = LayoutEditPlan.owner(m, "b_space");
        t("owner of shared bottom key = bot", ob != null && ob[0].equals("bot"));
        t("ownRow own key", LayoutEditPlan.ownRow(m, "en", "en_q") >= 0);
        t("ownRow shared key -1", LayoutEditPlan.ownRow(m, "en", "b_space") == -1);
        t("ownRow other layer -1", LayoutEditPlan.ownRow(m, "pb", "en_q") == -1);
        String rn = LayoutEditPlan.neighbourId(m, "en", "en_q", 1);
        t("neighbour right of q = w", rn.equals("en_w"));
        t("neighbour left of q none", LayoutEditPlan.neighbourId(m, "en", "en_q", -1).length() == 0 || LayoutEditPlan.neighbourId(m, "en", "en_q", -1).startsWith("en_"));
        t("neighbour unknown", LayoutEditPlan.neighbourId(m, "en", "zz", 1).equals(""));
        t("keep same id", LayoutEditPlan.keepOrNear(m, "en", "en_q", null).equals("en_q"));
        LayoutDef del = LayoutOps.delete(m, "en_w");
        t("delete w really removed", LayoutEditPlan.findKey(del, "en", "en_w") == null && del != m);
        int[] loc = LayoutEditPlan.locate(m, "en", "en_w");
        String near = LayoutEditPlan.keepOrNear(del, "en", "en_w", loc);
        t("near after delete = e (took w's place)", near.equals("en_e"));
        t("near no loc = first char", LayoutEditPlan.keepOrNear(del, "en", "en_w", null).equals(LayoutEditPlan.firstCharId(del, "en")));
        t("near null model", LayoutEditPlan.keepOrNear(null, "en", "x", null).equals(""));
        t("near wild loc clamped", LayoutEditPlan.keepOrNear(del, "en", "gone", new int[] { 99, 99 }).length() > 0);
        t("summary text", LayoutEditPlan.rowSummary(m, "en", "en_q").startsWith("Row ") && LayoutEditPlan.rowSummary(m, "en", "en_q").contains("keys, width"));
        t("summary unknown", LayoutEditPlan.rowSummary(m, "en", "zz").equals(""));
        t("undo text off", LayoutEditPlan.undoText(false, "merge").equals("Undo"));
        t("undo text label", LayoutEditPlan.undoText(true, "merge").equals("Undo: merge"));
        t("undo text empty label", LayoutEditPlan.undoText(true, "").equals("Undo") && LayoutEditPlan.undoText(true, null).equals("Undo"));
        t("redo text", LayoutEditPlan.redoText(true, "split").equals("Redo: split") && LayoutEditPlan.redoText(false, "split").equals("Redo"));
        t("refused text", LayoutEditPlan.refusedText("merge").startsWith("Not possible here: merge") && LayoutEditPlan.refusedText(null).contains("this change"));
        // the buttons of the editor, run through the same ops + gate the editor uses (every result must stay valid / pass the safety gate)
        LayoutDef e1 = LayoutOps.makeSpacer(m, "en_w");
        LayoutDef e2 = LayoutOps.makeSpacer(e1, "en_e");
        LayoutDef e3 = LayoutOps.merge(e2, "en_w");
        t("two empty spots merge into one", e3 != e2 && LayoutEditPlan.findKey(e3, "en", "en_e") == null && eq(LayoutEditPlan.findKey(e3, "en", "en_w").weight, 2f) && LayoutEditPlan.findKey(e3, "en", "en_w").kind == KeyDef.SPACER);
        LayoutDef e4 = LayoutOps.split(e3, "en_w");
        t("split gives two again", e4 != e3 && eq(LayoutEditPlan.findKey(e4, "en", "en_w").weight, 1f));
        LayoutDef nolp = LayoutOps.setLp(m, "en_q", "");
        t("merge moves second letter note", LayoutOps.mergeMovesSecond(nolp, "en_q") && !LayoutOps.mergeMovesSecond(m, "en_q"));
        LayoutDef mg = LayoutOps.merge(nolp, "en_q");
        t("merge keeps first, second to long-press", LayoutEditPlan.findKey(mg, "en", "en_q").lp.equals("w") && LayoutEditPlan.findKey(mg, "en", "en_w") == null);
        LayoutDef fl = LayoutOps.fill(e1, "en_w", "x");
        t("fill an empty spot", fl != e1 && LayoutEditPlan.findKey(fl, "en", "en_w").out[0].equals("x"));
        t("fill a normal key refused", LayoutOps.fill(m, "en_q", "x") == m);
        t("move right then left = same order", LayoutEditPlan.neighbourId(LayoutOps.moveLeft(LayoutOps.moveRight(m, "en_q"), "en_q"), "en", "en_q", 1).equals("en_w"));
        t("add before gives a spacer", LayoutEditPlan.findKey(LayoutOps.insertBefore(m, "en_q"), "en", LayoutOps.newId(m)) != null);
        LayoutDef rr = LayoutOps.addRow(m, "en", LayoutEditPlan.ownRow(m, "en", "en_q"));
        t("add row valid", rr != m && LayoutOps.valid(rr) == null && rr.layer("en").rows.size() == m.layer("en").rows.size() + 1);
        t("delete row valid", LayoutOps.valid(LayoutOps.deleteRow(rr, "en", LayoutEditPlan.ownRow(rr, "en", "en_q") + 1)) == null);
        t("row height edit", LayoutOps.setRowHeight(m, "en", LayoutEditPlan.ownRow(m, "en", "en_q"), LayoutEditPlan.nextHeight(100, 1)) != m);
        t("auto balance valid", LayoutOps.valid(LayoutOps.autoBalanceRow(m, "en", LayoutEditPlan.ownRow(m, "en", "en_q"))) == null);
        t("swap rows valid", LayoutOps.valid(LayoutOps.swapRows(m, "en", LayoutEditPlan.ownRow(m, "en", "en_q"), LayoutEditPlan.ownRow(m, "en", "en_q") + 1)) == null);
        t("copy style next", LayoutOps.copyKeyStyle(LayoutOps.setWeight(m, "en_q", 2f), "en_q", "en_w") != null);
        // history (undo / redo as the editor uses it)
        LayoutHistory h = new LayoutHistory(m);
        h.commit(e1, "empty w");
        t("history can undo", h.canUndo() && LayoutEditPlan.undoText(h.canUndo(), h.undoLabel()).equals("Undo: empty w"));
        t("history undo gives start", h.undo().equals(m) && h.canRedo() && LayoutEditPlan.redoText(h.canRedo(), h.redoLabel()).equals("Redo: empty w"));
        // ---- v0.17.10 (K7) swipe fields, picker, warnings ----
        t("char has swipe field", LayoutEditPlan.has(LayoutEditPlan.fields(KeyDef.CHAR), LayoutEditPlan.F_SWIPE));
        t("action / spacer have no swipe field", !LayoutEditPlan.has(LayoutEditPlan.fields(KeyDef.ACTION), LayoutEditPlan.F_SWIPE) && !LayoutEditPlan.has(LayoutEditPlan.fields(KeyDef.SPACER), LayoutEditPlan.F_SWIPE));
        t("swipe limit", LayoutEditPlan.limit(LayoutEditPlan.F_SWIPE) == LayoutText.MAX_OUT);
        t("4 swipe names", LayoutEditPlan.SWIPE_NAMES.length == 4);
        String[] pt = LayoutEditPlan.pickTitles();
        t("8 picker groups", pt.length == 8);
        for (int gi = 0; gi < pt.length; gi++) {
            String[] it = LayoutEditPlan.pickItems(gi);
            boolean good = it.length > 0;
            java.util.HashSet<String> seen = new java.util.HashSet<String>();
            for (String x : it) {
                if (x.length() == 0 || !seen.add(x) || !LayoutText.cleanSwipe(x, LayoutText.MAX_OUT).equals(x)) { good = false; System.out.println("bad pick " + gi + " " + x); }
                if (gi == 7 && !LayoutText.AT_ACTIONS.contains(x)) good = false;
            }
            t("picker group " + gi + " clean + unique", good);
        }
        t("picker unknown group empty", LayoutEditPlan.pickItems(99).length == 0 && LayoutEditPlan.pickItems(-1).length == 0);
        t("picker has taka sign", java.util.Arrays.asList(LayoutEditPlan.pickItems(2)).contains("\u09F3"));
        t("picker actions = whole whitelist", LayoutEditPlan.pickItems(7).length == LayoutText.AT_ACTIONS.size());
        t("every @action has a name", allNamed());
        t("pick name zwj", LayoutEditPlan.pickName("\u200D").contains("ZWJ") && LayoutEditPlan.pickName("\u200C").contains("ZWNJ"));
        t("pick name action", LayoutEditPlan.pickName("@paste").equals("@paste = Paste"));
        // a picked swipe text goes through the real op
        LayoutDef sw = LayoutOps.setSwipe(m, "en_q", 0, "@paste");
        t("swipe action saved", sw != m && LayoutEditPlan.findKey(sw, "en", "en_q").sw[0].equals("@paste") && LayoutOps.valid(sw) == null);
        LayoutDef sw2 = LayoutOps.setSwipe(sw, "en_q", 2, "@bogus");
        t("unknown @action refused (cleaned)", LayoutEditPlan.findKey(sw2, "en", "en_q").sw[2].equals(""));
        LayoutDef sw3 = LayoutOps.setSwipe(m, "en_q", 1, "\u09F3");
        t("taka swipe", LayoutEditPlan.findKey(sw3, "en", "en_q").sw[1].equals("\u09F3"));
        // warnings
        t("defaults: no warning of this layer worse than info", LayoutEditPlan.warnKeyIds(LayoutCheck.check(m), "en", LayoutCheck.LOW).isEmpty());
        LayoutDef dup = LayoutOps.setOut(m, "en_w", 0, "q");
        java.util.List<LayoutCheck.Warn> dw = LayoutCheck.check(dup);
        java.util.Set<String> dk = LayoutEditPlan.warnKeyIds(dw, "en", LayoutCheck.LOW);
        t("duplicate: both keys outlined", dk.contains("en_q") && dk.contains("en_w"));
        t("duplicate: other layer untouched", LayoutEditPlan.warnKeyIds(dw, "pb", LayoutCheck.LOW).isEmpty());
        t("duplicate: warn line", LayoutEditPlan.warnLine(LayoutEditPlan.warnsOf(dw, "en").get(0)).startsWith("[") );
        LayoutCheck.Warn fx = null;
        for (LayoutCheck.Warn wd : dw) if (LayoutCheck.W_DUP.equals(wd.code)) fx = wd;
        LayoutDef fixed = fx == null ? dup : LayoutCheck.autoFix(dup, fx);
        t("auto-fix clears the duplicate", fx != null && fixed != dup && LayoutEditPlan.warnKeyIds(LayoutCheck.check(fixed), "en", LayoutCheck.MED).isEmpty());
        // coverage chips + first empty spot
        LayoutDef gone = LayoutOps.makeSpacer(m, "en_z");
        gone = LayoutOps.setOut(gone, "en_z", 0, "");
        java.util.List<String> chips = LayoutEditPlan.coverageItems(LayoutCheck.check(gone), "en");
        t("coverage chip z", chips.contains("z"));
        t("first empty spot found (is a spacer)", LayoutEditPlan.firstSpacerId(gone, "en").length() > 0 && LayoutEditPlan.findKey(gone, "en", LayoutEditPlan.firstSpacerId(gone, "en")).kind == KeyDef.SPACER);
        t("no empty spot = empty id", LayoutEditPlan.firstSpacerId(m, "en").equals("") || LayoutEditPlan.findKey(m, "en", LayoutEditPlan.firstSpacerId(m, "en")).kind == KeyDef.SPACER);
        LayoutDef put = LayoutOps.fill(gone, "en_z", "z");
        t("chip tap puts z back, warning gone", LayoutEditPlan.coverageItems(LayoutCheck.check(put), "en").isEmpty());
        t("warnsOf sorted worst first", sortedOk(LayoutEditPlan.warnsOf(LayoutCheck.check(gone), "en")));
        // v0.17.12 (K8)
        t("edit tabs 8", LayoutEditPlan.EDIT_TABS.length == 8 && LayoutEditPlan.editTitles().length == 8);
        t("edit tab id bad", LayoutEditPlan.editTabId(99).equals("en") && LayoutEditPlan.editTabId(3).equals("nr") && LayoutEditPlan.editTabId(7).equals("s3"));
        t("symbol layer test", LayoutEditPlan.isSymbolLayer("s1") && LayoutEditPlan.isSymbolLayer("s3") && !LayoutEditPlan.isSymbolLayer("nr") && !LayoutEditPlan.isSymbolLayer("s4") && !LayoutEditPlan.isSymbolLayer(null));
        for (String sl : new String[] { "s1", "s2", "s3" }) t("symbol layer rows " + sl, m.effectiveRows(sl).size() == 4);
        boolean allOk = true;
        for (String lay : new String[] { "en", "s1" }) for (String a1 : LayoutEditPlan.actionIds(lay)) if (!com.mgb.keyboard.layout.LayoutText.ACTIONS.contains(a1) || LayoutEditPlan.actionTitle(a1, lay).length() == 0) allOk = false;
        t("picker ids are valid actions", allOk);
        t("sym title by layer", LayoutEditPlan.actionTitle("sym", "s1").startsWith("ABC") && LayoutEditPlan.actionTitle("sym", "bot").startsWith("?123"));
        LayoutDef fk = LayoutOps.setAction(m, "s1_comma", "emoji");
        t("symbol key becomes function key", fk != m && LayoutEditPlan.findKey(fk, "s1", "s1_comma").kind == KeyDef.ACTION);
        t("severity names", LayoutEditPlan.severityName(3).equals("High") && LayoutEditPlan.severityName(0).equals("Info"));
        t("null safe", LayoutEditPlan.warnKeyIds(null, "en", 0).isEmpty() && LayoutEditPlan.coverageItems(null, "en").isEmpty() && LayoutEditPlan.warnsOf(null, "en").isEmpty() && LayoutEditPlan.warnLine(null).equals(""));
        System.out.println("LayoutEditPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
