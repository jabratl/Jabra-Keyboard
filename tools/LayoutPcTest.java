import com.mgb.keyboard.layout.*;
import com.mgb.keyboard.typing.PcActions;
import java.util.HashSet;
import java.util.Set;

/** v0.17.50 (PC2) tests: PC actions, the PC layer model (LayoutDefaults.pcModel), action validation (computer only, no Android). */
public class LayoutPcTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        // ---- PcActions
        t("24 key actions", PcActions.allKeyActions().length == 24);
        for (String s : PcActions.allKeyActions()) {
            t("valid " + s, PcActions.valid(s) && PcActions.isKey(s) && !PcActions.isMod(s));
            t("code > 0 " + s, PcActions.keyCode(s) > 0);
            t("label " + s, PcActions.label(s).length() > 0);
            t("survives cleanWord(16) " + s, LayoutText.cleanWord(s, 16).equals(s));
        }
        for (String s : new String[] { "pc_ctrl", "pc_alt", "pc_shift", "pc_win" }) {
            t("mod valid " + s, PcActions.valid(s) && PcActions.isMod(s) && !PcActions.isKey(s));
            t("mod has no key code " + s, PcActions.keyCode(s) == 0);
            t("mod label " + s, PcActions.label(s).length() > 0);
            t("mod survives cleanWord " + s, LayoutText.cleanWord(s, 16).equals(s));
            t("mod does not repeat " + s, !PcActions.isRepeat(s));
        }
        t("null / empty / unknown invalid", !PcActions.valid(null) && !PcActions.valid("") && !PcActions.valid("pc_key_x") && !PcActions.valid("pc_key_") && !PcActions.valid("pc_key_F5") && !PcActions.valid("shift"));
        t("code esc 111 tab 61 del 112 ins 124", PcActions.keyCode("pc_key_esc") == 111 && PcActions.keyCode("pc_key_tab") == 61 && PcActions.keyCode("pc_key_del") == 112 && PcActions.keyCode("pc_key_ins") == 124);
        t("code F1 131 .. F12 142", PcActions.keyCode("pc_key_f1") == 131 && PcActions.keyCode("pc_key_f5") == 135 && PcActions.keyCode("pc_key_f12") == 142);
        t("code home 122 end 123 pgup 92 pgdn 93", PcActions.keyCode("pc_key_home") == 122 && PcActions.keyCode("pc_key_end") == 123 && PcActions.keyCode("pc_key_pgup") == 92 && PcActions.keyCode("pc_key_pgdn") == 93);
        t("code arrows up 19 down 20 left 21 right 22", PcActions.keyCode("pc_key_up") == 19 && PcActions.keyCode("pc_key_down") == 20 && PcActions.keyCode("pc_key_left") == 21 && PcActions.keyCode("pc_key_right") == 22);
        Set<Integer> codes = new HashSet<Integer>();
        for (String s : PcActions.allKeyActions()) codes.add(PcActions.keyCode(s));
        t("key codes unique", codes.size() == 24);
        int rep = 0;
        for (String s : PcActions.allKeyActions()) if (PcActions.isRepeat(s)) rep++;
        t("7 repeat keys (Del PgUp PgDn + 4 arrows)", rep == 7 && PcActions.isRepeat("pc_key_del") && PcActions.isRepeat("pc_key_left") && !PcActions.isRepeat("pc_key_esc") && !PcActions.isRepeat("pc_key_f1") && !PcActions.isRepeat("pc_key_home"));
        t("labels", PcActions.label("pc_ctrl").equals("Ctrl") && PcActions.label("pc_win").equals("Win") && PcActions.label("pc_key_pgup").equals("PgUp") && PcActions.label("pc_key_f12").equals("F12") && PcActions.label("nope").isEmpty());
        t("text size: arrows 14, F-keys 10, Esc 12", PcActions.textSp("pc_key_up") == 14f && PcActions.textSp("pc_key_f3") == 10f && PcActions.textSp("pc_key_esc") == 12f);

        // ---- LayoutText.isAction
        t("isAction pc_ctrl", LayoutText.isAction("pc_ctrl"));
        t("isAction pc_key_f5", LayoutText.isAction("pc_key_f5"));
        t("isAction old ones still valid", LayoutText.isAction("shift") && LayoutText.isAction("backspace") && LayoutText.isAction("combo"));
        t("isAction unknown / null", !LayoutText.isAction("nope") && !LayoutText.isAction(null) && !LayoutText.isAction("pc_key_x"));

        // ---- PC layer model
        LayoutDef full = LayoutDefaults.pcModel(true);
        t("one layer 'pc'", full.layers.size() == 1 && full.layer("pc") != null);
        LayerDef l = full.layer("pc");
        t("3 rows with F-row", l.rows.size() == 3);
        t("row sizes 8 / 12 / 8", l.rows.get(0).keys.size() == 8 && l.rows.get(1).keys.size() == 12 && l.rows.get(2).keys.size() == 8);
        Set<String> ids = new HashSet<String>();
        boolean allAct = true, allValid = true, idsOk = true, hOk = true;
        for (RowDef r : l.rows) {
            if (r.heightPct != LayoutDefaults.PC_ROW_PCT || r.heightPct < RowDef.H_MIN || r.heightPct > RowDef.H_MAX) hOk = false;
            if (r.isRef()) allAct = false;
            for (KeyDef k : r.keys) {
                if (k.kind != KeyDef.ACTION) allAct = false;
                if (!PcActions.valid(k.action)) allValid = false;
                if (!ids.add(k.id)) idsOk = false;
                if (!LayoutText.cleanId(k.id).equals(k.id)) idsOk = false;
                if (k.label[0].length() == 0 || k.label[0].length() > LayoutText.MAX_LABEL) allValid = false;
            }
        }
        t("all keys are valid ACTION keys", allAct && allValid);
        t("ids unique + legal", idsOk && ids.size() == 28);
        t("row height 75 %", hOk);
        t("row 1 order", l.rows.get(0).keys.get(0).action.equals("pc_key_esc") && l.rows.get(0).keys.get(2).action.equals("pc_ctrl") && l.rows.get(0).keys.get(5).action.equals("pc_win") && l.rows.get(0).keys.get(7).action.equals("pc_key_ins"));
        t("row 2 is F1..F12", l.rows.get(1).keys.get(0).action.equals("pc_key_f1") && l.rows.get(1).keys.get(11).action.equals("pc_key_f12"));
        t("row 3 order", l.rows.get(2).keys.get(0).action.equals("pc_key_home") && l.rows.get(2).keys.get(2).action.equals("pc_key_up") && l.rows.get(2).keys.get(7).action.equals("pc_key_right"));

        LayoutDef compact = LayoutDefaults.pcModel(false);
        t("compact = 2 rows, no F-keys", compact.layer("pc").rows.size() == 2 && compact.layer("pc").rows.get(0).keys.size() == 8 && compact.layer("pc").rows.get(1).keys.size() == 8);
        boolean noF = true;
        for (RowDef r : compact.layer("pc").rows) for (KeyDef k : r.keys) if (k.action.startsWith("pc_key_f")) noF = false;
        t("compact has no F key", noF);

        // ---- the PC layer is NOT part of the saved default layout (rows appear only at draw time, only when PC mode is on)
        LayoutDef def = LayoutDefaults.build();
        t("default layout has no pc layer", def.layer("pc") == null);

        // ---- the rows are accepted by the layout gate (same rules as saved layouts), also after encode / decode
        LayoutDef host = LayoutDefaults.build();
        host.layer("en").rows.addAll(0, LayoutDefaults.pcModel(true).layer("pc").rows);
        String why = LayoutGuard.gateModel(host);
        t("gateModel ok with pc rows inside en (" + why + ")", why == null);
        String txt = LayoutCodec.encode(host);
        LayoutDef back = txt == null ? null : LayoutCodec.decode(txt);
        t("encode / decode round trip keeps pc actions", back != null && back.layer("en").sig().equals(host.layer("en").sig()) && back.layer("en").rows.get(0).keys.get(2).action.equals("pc_ctrl"));

        // ---- LayoutOps accepts the pc actions
        LayoutDef m2 = LayoutOps.setAction(LayoutDefaults.build(), "en_q", "pc_key_f5");
        KeyDef q = null;
        for (RowDef r : m2.layer("en").rows) for (KeyDef k : r.keys) if (k.id.equals("en_q")) q = k;
        t("setAction pc_key_f5", q != null && q.kind == KeyDef.ACTION && q.action.equals("pc_key_f5"));
        LayoutDef m3 = LayoutOps.setAction(LayoutDefaults.build(), "en_q", "pc_key_nope");
        KeyDef q3 = null;
        for (RowDef r : m3.layer("en").rows) for (KeyDef k : r.keys) if (k.id.equals("en_q")) q3 = k;
        t("setAction unknown pc action refused", q3 != null && q3.kind == KeyDef.CHAR);

        System.out.println("LayoutPcTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
