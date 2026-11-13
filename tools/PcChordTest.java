import com.mgb.keyboard.layout.*;
import com.mgb.keyboard.typing.PcActions;
import com.mgb.keyboard.typing.PcChord;
import com.mgb.keyboard.typing.PcKeyPlan;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** v0.17.51 (PC3) tests: PcChord parser, More / Num / chip actions, 4th row of the PC layer (computer only, no Android). */
public class PcChordTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    static PcChord.Chord p(String s) { return PcChord.parse(s); }

    public static void main(String[] a) {
        PcChord.Chord z = p("Ctrl+Shift+Z");
        t("Ctrl+Shift+Z parses", z != null && z.mods == (PcKeyPlan.MOD_CTRL | PcKeyPlan.MOD_SHIFT) && z.code == 54);
        t("Ctrl+Shift+Z text + label", z.text().equals("Ctrl+Shift+Z") && z.label().equals("^⇧Z"));
        t("case + spaces ignored", p("  ctrl + c ").code == 31 && p("CTRL+C").mods == PcKeyPlan.MOD_CTRL);
        t("letters a=29 z=54", p("a").code == 29 && p("z").code == 54 && p("A").code == 29);
        t("digits 0=7 9=16", p("Ctrl+0").code == 7 && p("Ctrl+9").code == 16);
        t("F keys F1=131 F12=142 F5", p("F1").code == 131 && p("f12").code == 142 && p("Alt+F4").code == 134 && p("Alt+F4").mods == PcKeyPlan.MOD_ALT);
        t("F0 F13 F01 invalid", p("F0") == null && p("F13") == null && p("F01") == null);
        t("named keys", p("Esc").code == 111 && p("Tab").code == 61 && p("Enter").code == 66 && p("Space").code == 62 && p("Backspace").code == 67 && p("Del").code == 112 && p("Insert").code == 124);
        t("nav keys", p("Home").code == 122 && p("End").code == 123 && p("PgUp").code == 92 && p("PageDown").code == 93 && p("Up").code == 19 && p("Right").code == 22);
        t("lock keys", p("PrtSc").code == 120 && p("ScrLk").code == 116 && p("Pause").code == 121 && p("Menu").code == 82 && p("Caps").code == 115 && p("NumLock").code == 143);
        t("punctuation", p("Ctrl+/").code == 76 && p("Ctrl+,").code == 55 && p("Ctrl+-").code == 69 && p("Ctrl+=").code == 70 && p("Ctrl+`").code == 68);
        t("Win aliases", p("Win+D").mods == PcKeyPlan.MOD_META && p("Meta+D").mods == PcKeyPlan.MOD_META && p("Cmd+D").mods == PcKeyPlan.MOD_META);
        t("all four mods", p("Ctrl+Alt+Shift+Win+Del").mods == 15);
        t("mod order in text", p("Win+Shift+Alt+Ctrl+Del").text().equals("Ctrl+Alt+Shift+Win+Del"));
        t("text parses back", p(p("Alt+Shift+PgUp").text()).code == 92 && p(p("Alt+Shift+PgUp").text()).mods == 6);
        t("label Alt+F4 / Win+D / ^C", p("Alt+F4").label().equals("Alt+F4") && p("Win+D").label().equals("Win+D") && p("ctrl+c").label().equals("^C"));
        t("only mods = invalid", p("Ctrl") == null && p("Ctrl+Shift") == null);
        t("two keys invalid", p("A+B") == null && p("Ctrl+A+B") == null);
        t("empty / null / junk invalid", p("") == null && p(null) == null && p("   ") == null && p("+") == null && p("Ctrl+") == null && p("+C") == null && p("Ctrl+Foo") == null && p("Ctrl++") == null);
        t("duplicate mod ok", p("Ctrl+Ctrl+C").mods == PcKeyPlan.MOD_CTRL);

        // lists
        List<PcChord.Chord> d = PcChord.parseList(PcChord.DEFAULT_LIST, 9);
        t("default list = 5 chips ^A ^C ^V ^X ^Z", d.size() == 5 && d.get(0).label().equals("^A") && d.get(4).label().equals("^Z"));
        t("junk dropped, order kept", PcChord.parseList("Ctrl+C, nope ,Ctrl+V,,Ctrl+", 5).size() == 2);
        t("duplicates dropped", PcChord.parseList("Ctrl+C,ctrl+c,CTRL + C", 5).size() == 1);
        t("max respected", PcChord.parseList("a,b,c,d,e,f,g", 3).size() == 3);
        t("separators ; and newline", PcChord.parseList("Ctrl+A;Ctrl+B\nCtrl+C", 5).size() == 3);
        t("null list = empty", PcChord.parseList(null, 5).isEmpty());
        t("chips(): empty / junk = default", PcChord.chips("").size() == 5 && PcChord.chips("zzz,qqq").size() == 5 && PcChord.chips(null).size() == 5);
        t("chips(): user text wins, max 5", PcChord.chips("Alt+F4,Win+D").size() == 2 && PcChord.chips("a,b,c,d,e,f,g,h").size() == PcChord.MAX_CHIPS);

        // actions
        t("chipIndex", PcActions.chipIndex("pc_chip_0") == 0 && PcActions.chipIndex("pc_chip_4") == 4 && PcActions.chipIndex("pc_chip_5") == -1
                && PcActions.chipIndex("pc_chip_") == -1 && PcActions.chipIndex("pc_chip_00") == -1 && PcActions.chipIndex(null) == -1 && PcActions.chipIndex("pc_key_f1") == -1);
        t("more / num / chips are valid but not mod / key", PcActions.valid("pc_more") && PcActions.valid("pc_num") && PcActions.valid("pc_chip_2")
                && !PcActions.isMod("pc_more") && !PcActions.isKey("pc_more") && PcActions.keyCode("pc_more") == 0 && !PcActions.isRepeat("pc_num"));
        t("old key set unchanged (24)", PcActions.allKeyActions().length == 24 && !PcActions.valid("pc_chip_9") && !PcActions.valid("pc_more_"));
        t("isAction pc_more / chip", LayoutText.isAction("pc_more") && LayoutText.isAction("pc_chip_1") && LayoutText.isAction("pc_num") && !LayoutText.isAction("pc_chip_7"));
        t("More panel 7 keys, codes", PcActions.moreCount() == 7 && PcActions.moreLabel(0).equals("PrtSc") && PcActions.moreCode(0) == 120 && PcActions.moreCode(6) == 58 && PcActions.moreCode(7) == 0 && PcActions.moreLabel(-1).isEmpty());
        Set<Integer> mc = new HashSet<Integer>();
        boolean moreOk = true;
        for (int i = 0; i < PcActions.moreCount(); i++) { if (PcActions.moreCode(i) <= 0 || PcActions.moreLabel(i).isEmpty() || !mc.add(PcActions.moreCode(i))) moreOk = false; }
        t("More codes unique, labels set", moreOk);
        t("pad 16 keys, 7 = NUMPAD_7 151, 0 = 144, + = 157, . = 158", PcActions.padCount() == 16 && PcActions.padCode(0) == 151 && PcActions.padLabel(0).equals("7") && PcActions.padCode(12) == 144 && PcActions.padCode(15) == 157 && PcActions.padCode(13) == 158 && PcActions.PAD_ENTER == 160);
        Set<Integer> pc = new HashSet<Integer>();
        boolean padOk = true;
        for (int i = 0; i < PcActions.padCount(); i++) { if (PcActions.padCode(i) < 144 || PcActions.padCode(i) > 161 || !pc.add(PcActions.padCode(i))) padOk = false; }
        t("pad codes unique, in NUMPAD range", padOk);

        // layer model with row 4
        LayoutDef old = LayoutDefaults.pcModel(true);
        t("old pcModel still 3 rows", old.layer("pc").rows.size() == 3);
        LayoutDef m = LayoutDefaults.pcModel(true, PcChord.chips(""));
        LayerDef l = m.layer("pc");
        t("new model 4 rows, first 3 equal old", l.rows.size() == 4 && l.rows.get(0).keys.size() == 8 && l.rows.get(1).keys.size() == 12 && l.rows.get(2).keys.size() == 8);
        RowDef r4 = l.rows.get(3);
        t("row 4 = 5 chips + More + Num", r4.keys.size() == 7 && r4.keys.get(0).action.equals("pc_chip_0") && r4.keys.get(4).action.equals("pc_chip_4") && r4.keys.get(5).action.equals("pc_more") && r4.keys.get(6).action.equals("pc_num"));
        t("chip labels = chord labels", r4.keys.get(0).label[0].equals("^A") && r4.keys.get(1).label[0].equals("^C") && r4.keys.get(4).label[0].equals("^Z"));
        Set<String> ids = new HashSet<String>();
        boolean valid = true, idsOk = true;
        for (RowDef r : l.rows) for (KeyDef k : r.keys) {
            if (k.kind != KeyDef.ACTION || !LayoutText.isAction(k.action)) valid = false;
            if (!ids.add(k.id) || !LayoutText.cleanId(k.id).equals(k.id)) idsOk = false;
            if (k.label[0].length() == 0 || k.label[0].length() > LayoutText.MAX_LABEL) valid = false;
        }
        t("all keys valid actions + labels", valid);
        t("ids unique + legal (28 + 7)", idsOk && ids.size() == 35);
        t("row 4 height 75 %", r4.heightPct == LayoutDefaults.PC_ROW_PCT);
        LayoutDef noChips = LayoutDefaults.pcModel(false, null);
        t("no chips: row 4 = More + Num only, 3 rows with compact", noChips.layer("pc").rows.size() == 3 && noChips.layer("pc").rows.get(2).keys.size() == 2
                && noChips.layer("pc").rows.get(2).keys.get(0).action.equals("pc_more"));
        LayoutDef two = LayoutDefaults.pcModel(true, PcChord.chips("Alt+F4,Win+D"));
        t("2 chips = 4 keys", two.layer("pc").rows.get(3).keys.size() == 4 && two.layer("pc").rows.get(3).keys.get(1).label[0].equals("Win+D"));
        t("saved default layout has no pc layer", LayoutDefaults.build().layer("pc") == null);

        System.out.println("PcChordTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
