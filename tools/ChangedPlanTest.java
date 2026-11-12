import com.mgb.keyboard.typing.ChangedPlan;
import java.util.Arrays;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/ChangedPlan.java tools/ChangedPlanTest.java && java -cp out ChangedPlanTest */
public class ChangedPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (java.util.Objects.equals(a, b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    public static void main(String[] x) {
        eq("unset", ChangedPlan.isChanged(-1, -1), false);
        eq("unset vs def 1", ChangedPlan.isChanged(-1, 1), false);
        eq("same", ChangedPlan.isChanged(1, 1), false);
        eq("diff", ChangedPlan.isChanged(0, 1), true);
        eq("stepper set (def -1)", ChangedPlan.isChanged(120, -1), true);
        eq("zero vs -1", ChangedPlan.isChanged(0, -1), true);
        eq("seg 2 short", ChangedPlan.useSegments(2, 14), true);
        eq("seg 4 short", ChangedPlan.useSegments(4, 20), true);
        eq("seg 5", ChangedPlan.useSegments(5, 10), false);
        eq("seg 1", ChangedPlan.useSegments(1, 3), false);
        eq("seg long", ChangedPlan.useSegments(3, 60), false);
        eq("repeat slow", ChangedPlan.repeatMs(0), 220);
        eq("repeat mid", ChangedPlan.repeatMs(5), 120);
        eq("repeat fast", ChangedPlan.repeatMs(50), 60);
        eq("repeat neg", ChangedPlan.repeatMs(-3), 220);
        boolean mono = true; int prev = 1000;
        for (int i = 0; i < 40; i++) { int v = ChangedPlan.repeatMs(i); if (v > prev) mono = false; prev = v; }
        eq("repeat never slower", mono, true);
        // sectionOf always lands in sections(group) or ""
        String[][] rows = {
            {"Layout & size", "kh_pct_p", "Size"}, {"Layout & size", "letter_bold", "Size"}, {"Layout & size", "row_gap", "Spacing"},
            {"Layout & size", "key_radius", "Shape"}, {"Layout & size", "pb_home_gap", "Bottom row"}, {"Layout & size", "oh_w_p", "One-hand"},
            {"Look", "theme", "Theme & style"}, {"Look", "onehand", "Other"}, {"Look", "hint_size_lv", "Hints"},
            {"Languages & layouts", "bn_layout", "Bangla"}, {"Languages & layouts", "sp_step", "Switching"}, {"Languages & layouts", "pc_mode", "Number row & mode"},
            {"Keys & gestures", "haptic", "Feedback"}, {"Keys & gestures", "ac_on", "Smart typing"}, {"Keys & gestures", "cursor_speed", "Timing"},
            {"Typing", "autocap", ""}, {"Clipboard & tools", "clip_on", ""}, {"Look", "nope", ""}, {null, "x", ""}, {"Look", null, ""},
        };
        for (String[] r : rows) eq("sectionOf " + r[0] + "/" + r[1], ChangedPlan.sectionOf(r[0], r[1]), r[2]);
        for (String g : new String[]{"Layout & size", "Look", "Languages & layouts", "Keys & gestures"}) {
            eq("sections non-empty " + g, ChangedPlan.sections(g).length > 0, true);
            for (String s : ChangedPlan.sections(g)) {
                boolean used = false;
                String[] keys = { "kh_pct_p","letter_pct","letter_bold","key_gap","row_gap","edge_dp","row_vgap","fn_gap","key_radius","bot_style","key_dots","pb_home_gap","oh_w_p",
                    "theme","look","key_border","icons_on","numrow_large","numrow_bn","hint_hide","hint_digits","hint_size_lv","hint_alpha_lv","onehand","tb_tip",
                    "bn_layout","lang_avro_on","lang_probhat_on","lang_tap","lang_fav","space_lang","sp_preview","sp_tick","sp_step","key_lang","numrow","numpad_auto","pc_mode","mode_key","pc_remember",
                    "haptic","fb_down","haptic_native","sound","key_popup","pop_ref","pop_fast","learn_on","sp_punct","ac_on","sg_on","ap_on","qa_on","h_lock","key_globe","key_voice","key_emoji","lp_delay","cursor_speed" };
                for (String k : keys) if (s.equals(ChangedPlan.sectionOf(g, k))) used = true;
                eq("section used " + g + "/" + s, used, true);
            }
        }
        eq("no sections typing", ChangedPlan.sections("Typing").length, 0);
        eq("no sections null", ChangedPlan.sections(null).length, 0);
        System.out.println("ChangedPlanTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
