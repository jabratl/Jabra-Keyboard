import com.mgb.keyboard.typing.SettingsIndex;
import com.mgb.keyboard.typing.SettingsIndex.Entry;
import java.util.*;

public class SettingsIndexTest {
    static int ok = 0, bad = 0;
    static void check(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    public static void main(String[] a) {
        List<Entry> all = new ArrayList<>();
        all.add(new Entry("haptic", "Vibrate", SettingsIndex.help("haptic"), "Keys & gestures"));
        all.add(new Entry("sound", "Sound", SettingsIndex.help("sound"), "Keys & gestures"));
        all.add(new Entry("theme", "Theme", SettingsIndex.help("theme"), "Look"));
        all.add(new Entry("key_popup", "Key popup", SettingsIndex.help("key_popup"), "Keys & gestures"));
        all.add(new Entry("ds_period", "Double-space period", SettingsIndex.help("ds_period"), "Typing"));

        check("empty query = all", SettingsIndex.search(all, "").size() == 5);
        check("null query = all", SettingsIndex.search(all, null).size() == 5);
        check("title", SettingsIndex.search(all, "vibr").size() == 1);
        check("case-insens", SettingsIndex.search(all, "VIBRATE").size() == 1);
        check("group word", SettingsIndex.search(all, "look").size() >= 1);
        check("help word", SettingsIndex.search(all, "bubble").size() == 1);
        check("key id with underscore", SettingsIndex.search(all, "key popup").size() >= 1);
        check("two words AND", SettingsIndex.search(all, "double period").size() == 1);
        check("two words no match", SettingsIndex.search(all, "double theme").size() == 0);
        check("no match", SettingsIndex.search(all, "zzzz").size() == 0);
        check("extra spaces", SettingsIndex.search(all, "  sound   ").size() == 1);
        check("null list", SettingsIndex.search(null, "x").size() == 0);
        check("null entry", !SettingsIndex.matches(null, "x"));

        String r = "";
        r = SettingsIndex.pushRecent(r, "a");
        r = SettingsIndex.pushRecent(r, "b");
        check("recent order", r.equals("b,a"));
        r = SettingsIndex.pushRecent(r, "a");
        check("recent dedupe moves front", r.equals("a,b"));
        for (int i = 0; i < 12; i++) r = SettingsIndex.pushRecent(r, "k" + i);
        check("recent cap 8", SettingsIndex.parseRecent(r).size() == 8);
        check("recent newest first", SettingsIndex.parseRecent(r).get(0).equals("k11"));
        check("recent oldest dropped", !SettingsIndex.parseRecent(r).contains("a"));
        check("push bad key ignored", SettingsIndex.pushRecent("x", "a,b").equals("x"));
        check("push empty ignored", SettingsIndex.pushRecent("x", "").equals("x"));
        check("parse null", SettingsIndex.parseRecent(null).isEmpty());
        check("parse dup + blanks", SettingsIndex.parseRecent("a,,a, b ,").size() == 2);
        check("parse long csv capped", SettingsIndex.parseRecent("1,2,3,4,5,6,7,8,9,10").size() == 8);

        check("encode", SettingsIndex.encodeLast("haptic", 1).equals("haptic|1"));
        String[] p = SettingsIndex.parseLast("haptic|1");
        check("parse last", p != null && p[0].equals("haptic") && p[1].equals("1"));
        p = SettingsIndex.parseLast("theme|-3");
        check("negative value", p != null && p[1].equals("-3"));
        check("parse empty", SettingsIndex.parseLast("") == null);
        check("parse null", SettingsIndex.parseLast(null) == null);
        check("parse no bar", SettingsIndex.parseLast("haptic") == null);
        check("parse bad number", SettingsIndex.parseLast("haptic|x") == null);
        check("parse trailing bar", SettingsIndex.parseLast("haptic|") == null);
        check("encode bad key", SettingsIndex.encodeLast("a|b", 1).isEmpty());

        String[] keys = { "sp_punct","ac_on","learn_on","lang_tap","lang_fav","lang_avro_on","lang_probhat_on","bn_layout","bn_map","numrow","numpad_auto","pc_mode","mode_key","pc_remember","bs_repeat","bs_word","autocap","enter_action","ds_period","ds_tab","hint_on","theme","look","key_border","onehand","icons_on","numrow_large","hint_hide","haptic","sound","key_popup","key_globe","key_voice","key_emoji","kb_custom","key_qm","danda_key","sp_wpct","sp_hpct","sp_anim","lp_delay","cursor_speed","edit_bar","clip_on","fmt_on","clip_lines","clip_view","clip_sort","clip_count","clip_expire","clip_sens","tb_vault","sp_preview","numrow_bn","pop_ref","h_lock","sp_tick","sp_step","pop_fast","sg_on","ap_on","clip_fit","clip_hold","clip_btn_pct","clip_card_pct","clip_stay","clip_fmt_tap","clip_list_pct","clip_order","kh_pct_l","oh_w_l","tb_custom","tb_auto","tb_fold","hint_custom","xrow_chars","vault_sort","vault_dest","tb_hpct","tb_ipct","nr_hpct","nr_mode","nr_chars","panel_ui" };
        check("panel_ui help names Gear + More (v0.16.11)", SettingsIndex.help("panel_ui").contains("Gear") && SettingsIndex.help("panel_ui").contains("More"));
        check("panel_ui help names Toolbar (v0.16.12)", SettingsIndex.help("panel_ui").contains("Toolbar") && SettingsIndex.help("panel_ui").length() <= 90);
        boolean all33 = true;
        for (String k : keys) { String h = SettingsIndex.help(k); if (h.isEmpty() || h.length() > 90) { all33 = false; System.out.println("help issue: " + k + " len " + h.length()); } }
        check("81 help lines exist and are one short line", all33);   // v0.17.40: +clip_fmt_tap; v0.17.38: +clip_list_pct; v0.17.37: +clip_stay; v0.17.5: +kb_custom; v0.17.2: +sp_wpct, sp_hpct, sp_anim; v0.17.1: +key_qm; v0.16.16: +clip_hold; v0.16.9: +panel_ui; v0.16.6: +tb_hpct, tb_ipct; v0.16.3: +bn_map; v0.7.1: +tb_vault; v0.10.10: +clip_sens; v0.15.1: +clip_fit; v0.15.2: +clip_order; v0.15.4: +9 app rows
        // v0.7.1: the Vault toolbar row is found by search (by title, help and key words)
        List<Entry> v = new ArrayList<>();
        v.add(new Entry("tb_vault", "Vault key (toolbar)", SettingsIndex.help("tb_vault"), "Clipboard & tools"));
        v.add(new Entry("clip_on", "Clip history", SettingsIndex.help("clip_on"), "Clipboard & tools"));
        check("vault row found by 'vault'", SettingsIndex.search(v, "vault").size() == 1);
        check("vault row found by help word 'templates'", SettingsIndex.search(v, "templates").size() == 1);
        check("vault row found by key words 'tb vault'", SettingsIndex.search(v, "tb vault").size() == 1);
        check("vault help is one short line", !SettingsIndex.help("tb_vault").isEmpty() && SettingsIndex.help("tb_vault").length() <= 90);
        check("unknown help empty", SettingsIndex.help("nope").isEmpty() && SettingsIndex.help(null).isEmpty());

        System.out.println("SettingsIndexTest " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " FAILED"));
        if (bad > 0) System.exit(1);
    }
}
