import com.mgb.keyboard.layout.*;

/** Run: bash tools/run_tests.sh  (or javac all app java + this file, then java -cp out LayoutHistoryTest) */
public class LayoutHistoryTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a == null ? b == null : a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }

    static LayoutDef model(float w) {
        LayoutDef m = new LayoutDef();
        LayerDef l = new LayerDef("en");
        RowDef r = new RowDef();
        r.keys.add(KeyDef.ch("a", "a", w));
        l.rows.add(r); m.layers.add(l);
        return m;
    }

    public static void main(String[] x) {
        LayoutDef m0 = model(1f), m1 = model(2f), m2 = model(3f), m3 = model(4f);
        LayoutHistory h = new LayoutHistory(m0);
        eq("start current", h.current() == m0, true);
        eq("start no undo", h.canUndo(), false);
        eq("start no redo", h.canRedo(), false);
        eq("undo at start = null", h.undo(), null);
        eq("redo at start = null", h.redo(), null);
        eq("undoLabel empty", h.undoLabel(), "");
        eq("commit new", h.commit(m1, "width 2"), true);
        eq("current after commit", h.current() == m1, true);
        eq("can undo", h.canUndo(), true);
        eq("undo label", h.undoLabel(), "width 2");
        eq("commit same instance ignored", h.commit(m1, "again"), false);
        eq("commit equal copy ignored", h.commit(m1.copy(), "copy"), false);
        eq("commit null ignored", h.commit(null, "x"), false);
        eq("undo steps 1", h.undoSteps(), 1);
        eq("commit 2", h.commit(m2, "width 3"), true);
        eq("commit 3", h.commit(m3, null), true);
        eq("null label = empty", h.undoLabel(), "");
        eq("undo -> m2", h.undo() == m2, true);
        eq("undo label now", h.undoLabel(), "width 3");
        eq("redo available", h.canRedo(), true);
        eq("redo label", h.redoLabel(), "");
        eq("undo -> m1", h.undo() == m1, true);
        eq("undo -> m0", h.undo() == m0, true);
        eq("no more undo", h.canUndo(), false);
        eq("redo -> m1", h.redo() == m1, true);
        eq("redo label m2", h.redoLabel(), "width 3");
        eq("redo steps 2", h.redoSteps(), 2);
        eq("new commit clears redo", h.commit(model(5f), "five"), true);
        eq("redo gone", h.canRedo(), false);
        eq("redo steps 0", h.redoSteps(), 0);
        eq("undo after branch -> m1", h.undo() == m1, true);
        h.reset(m3);
        eq("reset current", h.current() == m3, true);
        eq("reset no undo", h.canUndo(), false);
        // limit
        LayoutHistory g = new LayoutHistory(model(1f));
        for (int i = 0; i < 120; i++) g.commit(model(1f + (i % 7) * 0.25f + (i / 7) * 0.001f + 0.5f + i), "c" + i);
        int steps = 0;
        while (g.undo() != null) steps++;
        eq("max 50 undo steps", steps, LayoutHistory.MAX);
        int redo = 0;
        while (g.redo() != null) redo++;
        eq("redo back to the newest", redo, LayoutHistory.MAX);
        eq("newest label kept", g.undoLabel(), "c119");
        // history with LayoutOps models
        LayoutDef b = new LayoutDef();
        LayerDef l = new LayerDef("en"); RowDef r = new RowDef();
        r.keys.add(KeyDef.ch("q", "q", 1f)); r.keys.add(KeyDef.ch("w", "w", 1f));
        l.rows.add(r); b.layers.add(l);
        LayoutHistory k = new LayoutHistory(b);
        LayoutDef merged = LayoutOps.merge(b, "q");
        eq("ops model recorded", k.commit(merged, "merge q + w"), true);
        eq("merged has 1 key", k.current().layer("en").rows.get(0).keys.size(), 1);
        eq("undo restores 2 keys", k.undo().layer("en").rows.get(0).keys.size(), 2);
        eq("old model untouched by ops", b.layer("en").rows.get(0).keys.size(), 2);
        System.out.println("LayoutHistoryTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
