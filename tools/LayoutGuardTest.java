import com.mgb.keyboard.layout.*;

/** v0.17.5 (K5a.1) safety net decisions: load gate, crash counter, kb_custom value. */
public class LayoutGuardTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        LayoutDef d = LayoutDefaults.build();
        String good = LayoutCodec.encode(d);
        t("defaults encode", good != null && good.startsWith("JKL1"));
        LayoutGuard.Gate g = LayoutGuard.gate(good);
        t("defaults pass the gate", g.ok() && g.code.equals(LayoutGuard.OK) && g.model != null);
        t("null = empty", LayoutGuard.gate(null).code.equals(LayoutGuard.EMPTY) && !LayoutGuard.gate(null).ok());
        t("blank = empty", LayoutGuard.gate("  \n ").code.equals(LayoutGuard.EMPTY));
        t("garbage = bad_text", LayoutGuard.gate("x").code.equals(LayoutGuard.BAD_TEXT));
        t("garbage 2", LayoutGuard.gate("hello world\nR 1 2 3").code.equals(LayoutGuard.BAD_TEXT));
        t("truncated defaults never throw", noThrow(good.substring(0, good.length() / 2)));
        t("header only", !LayoutGuard.gate("JKL1").ok());
        t("newer version = refused", !LayoutGuard.gate("JKL9\nL en\n").ok());
        StringBuilder big = new StringBuilder(good);
        for (int i = 0; i < 70000; i++) big.append('x');
        t("oversize = refused", !LayoutGuard.gate(big.toString()).ok());
        // fatal: no backspace in the English layer
        LayoutDef nb = d.copy();
        for (RowDef r : nb.layer("bot").rows) for (int i = r.keys.size() - 1; i >= 0; i--) if (r.keys.get(i).action.equals("sym")) r.keys.remove(i);
        for (LayerDef l : nb.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) { for (int i = 0; i < 4; i++) if (k.sw[i].equals("@sym")) k.sw[i] = ""; }
        LayoutGuard.Gate gf = LayoutGuard.gate(LayoutCodec.encode(nb));
        t("no symbols key = fatal", !gf.ok() && gf.code.equals(LayoutGuard.FATAL) && gf.reason.length() > 0);
        t("gateModel ok on defaults", LayoutGuard.gateModel(d) == null);
        t("gateModel refuses null", LayoutGuard.gateModel(null) != null);
        t("gateModel refuses fatal", LayoutGuard.gateModel(nb) != null);
        LayoutDef empty = new LayoutDef();
        t("gateModel refuses empty model", LayoutGuard.gateModel(empty) != null);
        t("trip 0", !LayoutGuard.tripped(0));
        t("trip 2", !LayoutGuard.tripped(2));
        t("trip 3", LayoutGuard.tripped(3));
        t("trip 99", LayoutGuard.tripped(99));
        t("armed 0 -> 1", LayoutGuard.armed(0) == 1);
        t("armed 2 -> 3", LayoutGuard.armed(2) == 3);
        t("armed negative -> 1", LayoutGuard.armed(-5) == 1);
        t("armed capped", LayoutGuard.armed(100000) == 100);
        t("customOn 1", LayoutGuard.customOn(1));
        t("customOn 0", !LayoutGuard.customOn(0));
        t("customOn 2 = off", !LayoutGuard.customOn(2));
        t("customOn -1 = off", !LayoutGuard.customOn(-1));
        // fuzz: random garbage never throws
        java.util.Random rnd = new java.util.Random(17);
        boolean allOk = true;
        for (int i = 0; i < 300; i++) {
            StringBuilder b = new StringBuilder();
            int n = rnd.nextInt(200);
            for (int j = 0; j < n; j++) b.append((char) (rnd.nextInt(3) == 0 ? 10 : 32 + rnd.nextInt(200)));
            try { LayoutGuard.gate(b.toString()); } catch (Throwable e) { allOk = false; }
        }
        t("fuzz never throws", allOk);
        System.out.println("LayoutGuardTest: " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }

    static boolean noThrow(String s) { try { LayoutGuard.gate(s); return true; } catch (Throwable e) { return false; } }
}
