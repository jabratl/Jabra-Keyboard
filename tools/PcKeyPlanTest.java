import com.mgb.keyboard.typing.PcKeyPlan;
import com.mgb.keyboard.typing.PcKeyPlan.Ev;
import com.mgb.keyboard.typing.PcState;
import java.util.List;

/** v0.17.49 (PC1) tests for PcKeyPlan (computer only, no Android). */
public class PcKeyPlanTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static final int KEY_C = 31, KEY_Z = 54, KEY_LEFT = 21;
    static final int C = PcKeyPlan.MOD_CTRL, A = PcKeyPlan.MOD_ALT, S = PcKeyPlan.MOD_SHIFT, M = PcKeyPlan.MOD_META;

    public static void main(String[] a) {
        List<Ev> p = PcKeyPlan.plan(KEY_C, 0);
        t("no mods = key down + key up only", p.size() == 2 && p.get(0).action == 0 && p.get(1).action == 1 && p.get(0).code == KEY_C && p.get(0).meta == 0);

        p = PcKeyPlan.plan(KEY_C, C);
        t("ctrl+c = 4 events", p.size() == 4);
        t("order: ctrl down, c down, c up, ctrl up",
            p.get(0).action == 0 && p.get(0).code == PcKeyPlan.KC_CTRL_LEFT
            && p.get(1).action == 0 && p.get(1).code == KEY_C
            && p.get(2).action == 1 && p.get(2).code == KEY_C
            && p.get(3).action == 1 && p.get(3).code == PcKeyPlan.KC_CTRL_LEFT);
        t("ctrl meta has plain + LEFT bit", p.get(1).meta == (0x1000 | 0x2000) && p.get(2).meta == (0x1000 | 0x2000));
        t("ctrl down event already carries ctrl meta, ctrl up carries none", p.get(0).meta == 0x3000 && p.get(3).meta == 0);

        p = PcKeyPlan.plan(KEY_Z, C | S);
        t("ctrl+shift+z = 6 events", p.size() == 6);
        t("mods down ctrl then shift", p.get(0).code == PcKeyPlan.KC_CTRL_LEFT && p.get(1).code == PcKeyPlan.KC_SHIFT_LEFT && p.get(2).code == KEY_Z && p.get(2).action == 0);
        t("mods up in reverse: shift then ctrl", p.get(4).code == PcKeyPlan.KC_SHIFT_LEFT && p.get(4).action == 1 && p.get(5).code == PcKeyPlan.KC_CTRL_LEFT && p.get(5).action == 1);
        t("meta grows on down", p.get(0).meta == PcKeyPlan.realMeta(C) && p.get(1).meta == PcKeyPlan.realMeta(C | S));
        t("meta shrinks on up", p.get(4).meta == PcKeyPlan.realMeta(C) && p.get(5).meta == 0);

        p = PcKeyPlan.plan(KEY_LEFT, C | A | S | M);
        t("all four mods = 10 events", p.size() == 10);
        t("all four: fixed order ctrl alt shift meta",
            p.get(0).code == PcKeyPlan.KC_CTRL_LEFT && p.get(1).code == PcKeyPlan.KC_ALT_LEFT && p.get(2).code == PcKeyPlan.KC_SHIFT_LEFT && p.get(3).code == PcKeyPlan.KC_META_LEFT);
        t("all four: ups reversed", p.get(6).code == PcKeyPlan.KC_META_LEFT && p.get(7).code == PcKeyPlan.KC_SHIFT_LEFT && p.get(8).code == PcKeyPlan.KC_ALT_LEFT && p.get(9).code == PcKeyPlan.KC_CTRL_LEFT);
        int downs = 0, ups = 0;
        for (Ev e : p) { if (e.action == 0) downs++; else ups++; }
        t("every down has an up (nothing stuck)", downs == 5 && ups == 5);
        t("last event leaves no meta", p.get(9).meta == 0);

        // real meta values
        t("realMeta ctrl = 0x3000", PcKeyPlan.realMeta(C) == 0x3000);
        t("realMeta alt = 0x12", PcKeyPlan.realMeta(A) == 0x12);
        t("realMeta shift = 0x41", PcKeyPlan.realMeta(S) == 0x41);
        t("realMeta meta = 0x30000", PcKeyPlan.realMeta(M) == 0x30000);
        t("legacyMeta = plain bits", PcKeyPlan.legacyMeta(C | A | S | M) == (0x1000 | 0x02 | 0x01 | 0x10000));
        t("legacyMeta equals PcState.meta()", PcKeyPlan.legacyMeta(C) == PcState.META_CTRL && PcKeyPlan.legacyMeta(A) == PcState.META_ALT);

        // modsFromMeta
        t("modsFromMeta plain ctrl", PcKeyPlan.modsFromMeta(0x1000) == C);
        t("modsFromMeta left + right bits", PcKeyPlan.modsFromMeta(0x2000 | 0x20 | 0x80) == (C | A | S));
        t("modsFromMeta none", PcKeyPlan.modsFromMeta(0) == 0);
        t("modsFromMeta round trip", PcKeyPlan.modsFromMeta(PcKeyPlan.realMeta(C | A | S | M)) == (C | A | S | M));

        // isModCode / modCode
        t("isModCode", PcKeyPlan.isModCode(113) && PcKeyPlan.isModCode(57) && PcKeyPlan.isModCode(59) && PcKeyPlan.isModCode(117) && !PcKeyPlan.isModCode(KEY_C));
        t("modCode unknown = 0", PcKeyPlan.modCode(0) == 0 && PcKeyPlan.modCode(16) == 0);

        // held key: mods down first, mods up at the end
        List<Ev> d = PcKeyPlan.modsDown(C | S);
        List<Ev> u = PcKeyPlan.modsUp(C | S);
        t("modsDown ctrl, shift", d.size() == 2 && d.get(0).code == PcKeyPlan.KC_CTRL_LEFT && d.get(1).code == PcKeyPlan.KC_SHIFT_LEFT && d.get(1).meta == PcKeyPlan.realMeta(C | S));
        t("modsUp shift, ctrl", u.size() == 2 && u.get(0).code == PcKeyPlan.KC_SHIFT_LEFT && u.get(1).code == PcKeyPlan.KC_CTRL_LEFT && u.get(1).meta == 0 && u.get(0).meta == PcKeyPlan.realMeta(C));
        t("modsDown / modsUp empty for no mods", PcKeyPlan.modsDown(0).isEmpty() && PcKeyPlan.modsUp(0).isEmpty());

        // PcState integration: LOCK keeps mods, ONCE cleared after send
        PcState s = new PcState();
        s.tapCtrl(1000); s.tapCtrl(1200);
        List<Ev> p1 = PcKeyPlan.plan(KEY_C, s.mods());
        s.keySent();
        List<Ev> p2 = PcKeyPlan.plan(KEY_C, s.mods());
        t("LOCK keeps mods: second plan equals first", p1.size() == 4 && p2.size() == 4 && p2.get(0).code == PcKeyPlan.KC_CTRL_LEFT);
        PcState o = new PcState();
        o.tapShift(1);
        List<Ev> q1 = PcKeyPlan.plan(KEY_C, o.mods());
        o.keySent();
        List<Ev> q2 = PcKeyPlan.plan(KEY_C, o.mods());
        t("ONCE: first plan has shift, next plan has none", q1.size() == 4 && q2.size() == 2);

        System.out.println("PcKeyPlanTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
