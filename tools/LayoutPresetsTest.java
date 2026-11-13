import com.mgb.keyboard.layout.ActionPlan;
import com.mgb.keyboard.layout.KeyDef;
import com.mgb.keyboard.layout.LayerDef;
import com.mgb.keyboard.layout.LayoutBook;
import com.mgb.keyboard.layout.LayoutCheck;
import com.mgb.keyboard.layout.LayoutCodec;
import com.mgb.keyboard.layout.LayoutDef;
import com.mgb.keyboard.layout.LayoutDefaults;
import com.mgb.keyboard.layout.LayoutGuard;
import com.mgb.keyboard.layout.LayoutPresets;
import com.mgb.keyboard.layout.RowDef;
import java.util.HashSet;
import java.util.Set;

/** v0.17.14 (K9b) tests of layout/LayoutPresets and the JKL1 name header (LayoutBook.withName / headerName). */
public class LayoutPresetsTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + n); } }

    static KeyDef key(LayoutDef m, String id) {
        for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) if (k.id.equals(id)) return k;
        return null;
    }

    public static void main(String[] a) {
        t("6 presets", LayoutPresets.COUNT == 6);
        t("title out of range", LayoutPresets.title(-1).equals("") && LayoutPresets.title(6).equals(""));
        t("note out of range", LayoutPresets.note(99).equals(""));
        t("build out of range = null", LayoutPresets.build(-1) == null && LayoutPresets.build(6) == null && LayoutPresets.text(7) == null);
        Set<String> names = new HashSet<String>();
        for (int i = 0; i < LayoutPresets.COUNT; i++) {
            String ti = LayoutPresets.title(i);
            t("p" + i + " title set", ti.length() > 0 && ti.length() <= LayoutBook.MAX_NAME);
            t("p" + i + " title is a clean name", LayoutBook.cleanName(ti).equals(ti));
            t("p" + i + " title unique", names.add(ti.toLowerCase()));
            t("p" + i + " note set", LayoutPresets.note(i).length() > 10);
            LayoutDef m = LayoutPresets.build(i);
            t("p" + i + " builds", m != null);
            if (m == null) continue;
            t("p" + i + " passes fatal", LayoutCheck.fatal(m).isEmpty());
            String raw = LayoutCodec.encode(m);
            t("p" + i + " encodes", raw != null && raw.length() < LayoutCodec.MAX_BYTES);
            String tx = LayoutPresets.text(i);
            t("p" + i + " text has name header", tx != null && tx.startsWith("JKL1 " + ti + "\n"));
            t("p" + i + " gate ok", LayoutGuard.gate(tx).ok());
            LayoutDef back = LayoutCodec.decode(tx);
            t("p" + i + " round trip", back != null && back.equals(m));
            t("p" + i + " header name read back", LayoutBook.headerName(tx).equals(ti));
            t("p" + i + " no HIGH warning", LayoutCheck.maxSeverity(LayoutCheck.check(m)) < LayoutCheck.HIGH);
            t("p" + i + " no duplicate-letter warning", noCode(m, LayoutCheck.W_DUP));
            t("p" + i + " no missing-key warning", noCode(m, LayoutCheck.W_MISSING) && noCode(m, LayoutCheck.W_NOEXIT));
            t("p" + i + " has en av pb s1 s2 s3 bot", m.layer("en") != null && m.layer("av") != null && m.layer("pb") != null && m.layer("s1") != null && m.layer("s3") != null && m.layer("bot") != null);
            t("p" + i + " deterministic", LayoutPresets.build(i).equals(m));
            boolean acts = true;
            for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) {
                if (k.kind == KeyDef.ACTION && !ActionPlan.actionHandled(k.action)) acts = false;
                for (String s : k.sw) if (s.startsWith("@") && s.length() > 1 && !ActionPlan.atHandled(s)) acts = false;
                if (k.lp.startsWith("@") && k.lp.length() > 1 && !ActionPlan.atHandled(k.lp)) acts = false;
            }
            t("p" + i + " only actions the keyboard runs", acts);
            t("p" + i + " fits a book", LayoutBook.empty().saveProblem(ti, tx).equals(""));
        }
        // classic = the plain default
        t("classic equals default", LayoutPresets.build(0).equals(LayoutDefaults.build()));
        // ridmik + tab
        LayoutDef r = LayoutPresets.build(1);
        t("ridmik: tab key", key(r, "b_tab") != null && key(r, "b_tab").action.equals("tab"));
        t("ridmik: space not below 2", key(r, "b_space").weight >= 2f);
        t("ridmik: tab before comma", idx(r, "b_tab") + 1 == idx(r, "b_comma"));
        t("ridmik: wider ?123 than classic", key(r, "b_sym").weight > key(LayoutDefaults.build(), "b_sym").weight);
        // coder
        LayoutDef c = LayoutPresets.build(2);
        t("coder: no number row", c.layer("nr") == null);
        t("coder: swipe down paste", key(c, "en_v").sw[1].equals("@paste") && key(c, "en_z").sw[1].equals("@undo"));
        t("coder: tab key", key(c, "b_tab") != null);
        t("coder: letters still there", key(c, "en_q") != null && key(c, "en_m") != null);
        // big
        LayoutDef g = LayoutPresets.build(3), d = LayoutDefaults.build();
        t("big: rows taller", g.layer("en").rows.get(1).heightPct > d.layer("en").rows.get(1).heightPct);
        t("big: same key count", g.totalKeys() == d.totalKeys());
        // bangla fast
        LayoutDef f = LayoutPresets.build(4);
        t("bangla: 12 extra keys in pb", f.layer("pb").keyCount() == d.layer("pb").keyCount() + 12);
        t("bangla: danda + taka + digits", key(f, "pb_f1").out[0].equals("\u0964") && key(f, "pb_f2").out[0].equals("\u09F3") && key(f, "pb_f3").out[0].equals("\u09E6") && key(f, "pb_f12").out[0].equals("\u09EF"));
        t("bangla: en layer unchanged", f.layer("en").sig().equals(d.layer("en").sig()));
        t("bangla: new row sits above the shared bottom row", f.layer("pb").rows.get(f.layer("pb").rows.size() - 1).isRef() && !f.layer("pb").rows.get(f.layer("pb").rows.size() - 2).isRef());
        // JKL1 header helpers
        String raw = LayoutCodec.encode(d);
        String named = LayoutBook.withName(raw, "  My\tlayout ");
        t("withName writes header", named.startsWith("JKL1 My layout\n"));
        t("withName keeps body", named.substring(named.indexOf('\n')).equals(raw.substring(raw.indexOf('\n'))));
        t("withName text still decodes", LayoutCodec.decode(named) != null && LayoutCodec.decode(named).equals(d));
        t("withName replaces an old name", LayoutBook.headerName(LayoutBook.withName(named, "Other")).equals("Other"));
        t("withName blank name unchanged", LayoutBook.withName(raw, "  ").equals(raw));
        t("withName null / not JKL unchanged", LayoutBook.withName(null, "x") == null && LayoutBook.withName("hello", "x").equals("hello"));
        t("headerName none", LayoutBook.headerName(raw).equals("") && LayoutBook.headerName(null).equals("") && LayoutBook.headerName("hello").equals(""));
        t("headerName cut to 24", LayoutBook.headerName("JKL1 123456789012345678901234567890\nL en\n").length() == 24);
        t("headerName leading blank lines", LayoutBook.headerName("\n JKL1 abc\nL en").equals("") || LayoutBook.headerName("\n JKL1 abc\nL en").equals("abc"));
        t("named text passes gate", LayoutGuard.gate(named).ok());
        t("named text in a book", LayoutBook.empty().saveAs("X", named).textOf("X").equals(named));
        // v0.17.23 (L5c) ridmik symbols
        LayoutDef rs = LayoutPresets.build(5);
        LayoutDef dd = LayoutDefaults.build();
        t("rsym: title", LayoutPresets.title(5).equals("Ridmik symbols"));
        t("rsym: tab key first on s1", key(rs, "s1_tab") != null && key(rs, "s1_tab").action.equals("tab") && rs.layer("s1").rows.get(0).keys.get(0).id.equals("s1_tab"));
        StringBuilder r1 = new StringBuilder();
        for (KeyDef k : rs.layer("s1").rows.get(0).keys) if (k.kind == KeyDef.CHAR) r1.append(k.out[0]);
        t("rsym: row 1 = ~ \\ < > _ = [ ]", r1.toString().equals("~\\<>_=[]"));
        StringBuilder r2 = new StringBuilder();
        for (KeyDef k : rs.layer("s1").rows.get(1).keys) r2.append(k.out[0]);
        t("rsym: row 2 = @ # taka % & * - + ( )", r2.toString().equals("@#\u09F3%&*-+()"));
        StringBuilder r3 = new StringBuilder();
        for (KeyDef k : rs.layer("s1").rows.get(2).keys) if (k.kind == KeyDef.CHAR) r3.append(k.out[0]);
        t("rsym: row 3 = ! \" ' : ; / ?", r3.toString().equals("!\"':;/?"));
        t("rsym: page key + backspace kept", key(rs, "s1_page") != null && key(rs, "s1_bksp") != null && key(rs, "s1_abc") != null);
        t("rsym: pages 2 / 3, letters, bottom row unchanged", same(rs.layer("s2"), dd.layer("s2")) && same(rs.layer("s3"), dd.layer("s3")) && same(rs.layer("en"), dd.layer("en")) && same(rs.layer("bot"), dd.layer("bot")));
        System.out.println("LayoutPresetsTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }

    /** v0.17.23: layers have no equals(): compare id / kind / outputs / action / weight / height of every key. */
    static boolean same(LayerDef a, LayerDef b) {
        if (a == null || b == null) return a == b;
        return sig(a).equals(sig(b));
    }

    static String sig(LayerDef l) {
        StringBuilder sb = new StringBuilder();
        for (RowDef r : l.rows) {
            sb.append('|').append(r.heightPct);
            for (KeyDef k : r.keys) sb.append(',').append(k.id).append(':').append(k.kind).append(':').append(k.out[0]).append(':').append(k.action).append(':').append(k.weight);
        }
        return sb.toString();
    }

    static boolean noCode(LayoutDef m, String code) {
        for (LayoutCheck.Warn w : LayoutCheck.check(m)) if (w.code.equals(code)) return false;
        return true;
    }

    static int idx(LayoutDef m, String id) {
        for (KeyDef k : m.layer("bot").rows.get(0).keys) if (k.id.equals(id)) return m.layer("bot").rows.get(0).keys.indexOf(k);
        return -99;
    }
}
