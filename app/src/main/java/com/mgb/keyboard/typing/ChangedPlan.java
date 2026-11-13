package com.mgb.keyboard.typing;

/**
 * v0.12.0 (M2 of the "App UI" plan): pure rules for the new setting rows. No Android classes.
 *  - isChanged: the small "changed from default" dot (unset = default).
 *  - useSegments: 2-4 short options become a segmented control, everything else a value chip that cycles.
 *  - repeatMs: press-and-hold repeat speed of the [-] [+] buttons.
 *  - sections: which section of a page a row belongs to (group name + pref key).
 */
public final class ChangedPlan {
    private ChangedPlan() { }

    /** cur < 0 = unset = default (never marked). Otherwise marked when it differs from def. */
    public static boolean isChanged(int cur, int def) { return cur >= 0 && cur != def; }

    public static final int SEG_MAX_OPTIONS = 4, SEG_MAX_CHARS = 26;
    public static boolean useSegments(int options, int totalChars) { return options >= 2 && options <= SEG_MAX_OPTIONS && totalChars <= SEG_MAX_CHARS; }

    public static final int HOLD_FIRST_MS = 450;
    /** Delay before repeat number [tick] (0 = first repeat). Starts slow, gets faster, never below 60 ms. */
    public static int repeatMs(int tick) {
        if (tick < 0) tick = 0;
        if (tick < 4) return 220;
        if (tick < 10) return 120;
        return 60;
    }

    // ---- sections ----
    private static final String[] G_LAYOUT = { "Size", "Spacing", "Shape", "Bottom row", "One-hand", "Keyboard layout" };   // v0.17.5 (K5a.1): + Keyboard layout
    private static final String[] G_LOOK = { "Theme & style", "Custom theme", "Number row", "Hints", "Other" };
    private static final String[] G_LANG = { "Bangla", "Switching", "Number row & mode" };
    private static final String[] G_KEYS = { "Feedback", "Popup", "Smart typing", "Extra keys", "Timing" };

    // v0.15.3 (N3): sections for the groups that had none
    private static final String[] G_TYPING = { "Editing", "Spaces & hints", "Custom characters" };
    private static final String[] G_CLIP = { "History", "Look of clips", "Privacy" };
    private static final String[] G_TOOLS = { "Toolbar", "Formatter", "Vault" };

    /** Section titles of a group (in order). Groups without sections: empty array. */
    public static String[] sections(String group) {
        if ("Layout & size".equals(group)) return G_LAYOUT.clone();
        if ("Look".equals(group)) return G_LOOK.clone();
        if ("Languages & layouts".equals(group)) return G_LANG.clone();
        if ("Keys & gestures".equals(group)) return G_KEYS.clone();
        if ("Typing".equals(group)) return G_TYPING.clone();
        if ("Clipboard & tools".equals(group)) return G_CLIP.clone();
        if ("Tools & Panels".equals(group)) return G_TOOLS.clone();
        return new String[0];
    }

    private static boolean in(String k, String... list) { for (String s : list) if (s.equals(k)) return true; return false; }

    /** Section of a row. "" = no section (the row stays directly in the group). Always one of sections(group) or "". */
    public static String sectionOf(String group, String key) {
        if (group == null || key == null) return "";
        if (group.equals("Layout & size")) {
            if (in(key, "kh_pct_p", "kh_pct_l", "letter_pct", "letter_bold")) return "Size";
            if (in(key, "key_gap", "row_gap", "edge_dp", "row_vgap", "fn_gap")) return "Spacing";
            if (in(key, "key_radius")) return "Shape";
            if (in(key, "bot_style", "key_dots", "pb_home_gap", "sp_wpct", "sp_hpct")) return "Bottom row";   // v0.17.2 (K2): sp_wpct, sp_hpct
            if (in(key, "oh_w_p", "oh_w_l")) return "One-hand";
            if (in(key, "kb_custom")) return "Keyboard layout";   // v0.17.5 (K5a.1)
        } else if (group.equals("Look")) {
            if (in(key, "theme", "look", "key_border", "icons_on")) return "Theme & style";
            if (in(key, "ct_studio")) return "Custom theme";   // v0.15.7 (N7): pseudo key (no row), keeps the section in use
            if (in(key, "numrow_large", "numrow_bn", "nr_hpct", "nr_mode", "nr_chars")) return "Number row";
            if (in(key, "hint_hide", "hint_digits", "hint_size_lv", "hint_alpha_lv")) return "Hints";
            if (in(key, "onehand", "tb_tip")) return "Other";
        } else if (group.equals("Languages & layouts")) {
            if (in(key, "bn_layout", "bn_map", "lang_avro_on", "lang_probhat_on")) return "Bangla";
            if (in(key, "lang_tap", "lang_fav", "space_lang", "sp_preview", "sp_tick", "sp_step", "key_lang", "sp_anim")) return "Switching";   // v0.17.2 (K2): sp_anim
            if (in(key, "numrow", "numpad_auto", "pc_mode", "mode_key", "pc_remember")) return "Number row & mode";
        } else if (group.equals("Keys & gestures")) {
            if (in(key, "haptic", "fb_down", "haptic_native", "sound")) return "Feedback";
            if (in(key, "key_popup", "pop_ref", "pop_fast")) return "Popup";
            if (in(key, "learn_on", "sp_punct", "ac_on", "sg_on", "ap_on", "qa_on")) return "Smart typing";
            if (in(key, "h_lock", "key_globe", "key_voice", "key_emoji", "key_qm", "danda_key")) return "Extra keys";
            if (in(key, "lp_delay", "cursor_speed")) return "Timing";
        } else if (group.equals("Typing")) {   // v0.15.3 (N3)
            if (in(key, "bs_repeat", "bs_word", "autocap", "enter_action")) return "Editing";
            if (in(key, "ds_period", "ds_tab", "hint_on")) return "Spaces & hints";
            if (in(key, "hint_custom", "xrow_chars")) return "Custom characters";
        } else if (group.equals("Clipboard & tools")) {
            if (in(key, "clip_on", "clip_count")) return "History";
            if (in(key, "clip_view", "clip_sort", "clip_order", "clip_lines", "clip_fit", "panel_ui", "clip_hold")) return "Look of clips";
            if (in(key, "clip_expire", "clip_sens")) return "Privacy";
        } else if (group.equals("Tools & Panels")) {
            if (in(key, "edit_bar", "tb_vault", "tb_custom", "tb_auto", "tb_fold", "tb_hpct", "tb_ipct")) return "Toolbar";
            if (in(key, "fmt_on")) return "Formatter";
            if (in(key, "vault_sort", "vault_dest")) return "Vault";
        }
        return "";
    }
}
