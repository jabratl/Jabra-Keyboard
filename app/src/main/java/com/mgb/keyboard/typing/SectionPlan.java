package com.mgb.keyboard.typing;

/**
 * v0.17.80 (U5a of the "App UI" plan): pure rules for BASIC / ADVANCED rows. No Android classes.
 * Pref app_adv (bool, default OFF). Pages with a "Show advanced" button: Typing, Look (U5a) + Layout & Size, Keys & Gestures, Clipboard & Tools, Tools & Panels (U5b). Data & About has no rows.
 * U5b also: collapsible section headers; pref sec_open = list of the sections the user CLOSED ("page/section" joined by "|"; absent = open, so nothing is hidden by default).
 * The SECTION of a row stays single-sourced in ChangedPlan.sectionOf (this class only forwards it, so the two can never drift).
 * A key that is not in the tables is BASIC (default), so no setting is ever hidden by accident.
 * A hidden (advanced) row shows itself when: the switch is ON, OR the row is changed from its default, OR it was opened from search / favourites.
 */
public final class SectionPlan {
    private SectionPlan() { }

    public static final int BASIC = 0, ADVANCED = 1;
    public static final String PREF = "app_adv";

    /** Pages (PagePlan ids) that have the "Show advanced" button. U5b: add the rest. */
    private static final String[] ADV_PAGES = { PagePlan.TYPING, PagePlan.LOOK, PagePlan.LAYOUT, PagePlan.KEYS, PagePlan.CLIP, PagePlan.TOOLS };   // v0.17.81 (U5b): Data & About has no setting rows (buttons only)

    // Typing page = groups "Languages & layouts" + "Typing"; Look page = group "Look". Every row key of these 3 groups is listed once.
    private static final String[] BASIC_KEYS = {
        "bn_layout", "lang_tap", "lang_en_on", "lang_avro_on", "lang_probhat_on", "space_lang", "key_lang", "numrow", "numpad_auto", "pc_mode", "mode_key",
        "bs_repeat", "autocap", "enter_action", "ds_period", "hint_on", "hint_mode",
        "theme", "look", "key_border", "icons_on", "numrow_large", "onehand", "bn_design", "app_tips",
        // v0.17.81 (U5b) Layout & size
        "kh_pct_p", "kh_pct_l", "key_gap", "letter_pct", "letter_bold", "key_radius", "edge_dp", "bot_style",
        // Keys & gestures
        "haptic", "sound", "key_popup", "learn_on", "ac_on", "sg_on", "key_globe", "key_voice", "key_emoji", "lp_delay",
        // Clipboard & tools
        "clip_on", "clip_view", "clip_sort", "clip_count", "clip_expire", "clip_new_look", "clip_sens", "clip_img",
        // Tools & Panels
        "edit_bar", "tb_vault", "tb_custom", "fmt_on"
    };
    private static final String[] ADV_KEYS = {
        "bn_map", "lang_fav", "lang_order", "sp_preview", "sp_tick", "sp_anim", "sp_step", "tools_keep", "bn_hints", "bn_numpage",
        "pc_remember", "pc_real_mods", "pc_layer", "pc_frow", "pc_chips", "pc_chips_list",
        "bs_word", "ds_tab", "lp_accents", "lp_punct", "hold_edit", "key_press_anim", "hint_custom", "xrow_chars",
        "numrow_bn", "nr_hpct", "nr_mode", "nr_chars", "hint_hide", "hint_digits", "hint_size_lv", "hint_alpha_lv", "tb_tip",
        // v0.17.81 (U5b) Layout & size
        "row_gap", "kb_custom", "row_vgap", "fn_gap", "sp_wpct", "sp_hpct", "key_dots", "pb_home_gap", "oh_w_p", "oh_w_l",
        // Keys & gestures
        "fb_down", "haptic_native", "pop_ref", "pop_fast", "sp_punct", "ap_on", "qa_on", "h_lock", "key_qm", "danda_key", "cursor_speed",
        // Clipboard & tools
        "clip_lines", "clip_order", "clip_stay", "clip_fmt_tap", "clip_fit", "panel_ui", "clip_btn_pct", "clip_card_pct", "clip_list_pct", "clip_hold",
        // Tools & Panels
        "tb_auto", "tb_fold", "tb_hpct", "tb_ipct", "vault_sort", "vault_dest"
    };

    private static boolean in(String[] l, String k) { if (k != null) for (String s : l) if (s.equals(k)) return true; return false; }

    public static String[] basicKeys() { return BASIC_KEYS.clone(); }
    public static String[] advancedKeys() { return ADV_KEYS.clone(); }

    /** true = the key has an explicit entry in the table. */
    public static boolean listed(String key) { return in(BASIC_KEYS, key) || in(ADV_KEYS, key); }

    public static int level(String key) { return in(ADV_KEYS, key) ? ADVANCED : BASIC; }
    public static boolean isAdvanced(String key) { return level(key) == ADVANCED; }

    /** Section of a row (forwarded to ChangedPlan, "" = no section). */
    public static String section(String group, String key) { return ChangedPlan.sectionOf(group, key); }

    /** Does this page have the "Show advanced" button? */
    public static boolean hasAdvanced(String pageId) { return in(ADV_PAGES, pageId); }

    /** Is a row visible? Basic rows always; advanced rows when the switch is ON, the row is changed, or it was opened on purpose (search hit / favourite). */
    public static boolean visible(boolean advOn, boolean advancedRow, boolean changed, boolean forced) {
        return !advancedRow || advOn || changed || forced;
    }

    /** Text of the page button. [hidden] = number of advanced rows that are hidden right now. */
    public static String buttonText(boolean advOn, int hidden) {
        if (advOn) return "Hide advanced settings";
        return hidden > 0 ? "Show advanced settings (" + hidden + " more)" : "Show advanced settings";
    }

    // ---- v0.17.81 (U5b): collapsible section headers. Pref sec_open holds the CLOSED sections only. ----
    public static final String PREF_CLOSED = "sec_open";
    public static final int MAX_CLOSED = 60;

    public static String secId(String page, String section) { return (page == null ? "" : page) + "/" + (section == null ? "" : section); }

    private static java.util.List<String> parse(String csv) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String p : csv.split("\\|")) if (!p.isEmpty() && !out.contains(p)) out.add(p);
        return out;
    }

    /** true = section is open (default; only listed sections are closed). */
    public static boolean isOpen(String csv, String id) { return !parse(csv).contains(id); }

    /** New pref text after the user opened / closed one section. Cap MAX_CLOSED (oldest dropped = they open again). */
    public static String setOpen(String csv, String id, boolean open) {
        java.util.List<String> l = parse(csv);
        l.remove(id);
        if (!open && id != null && !id.isEmpty()) l.add(id);
        while (l.size() > MAX_CLOSED) l.remove(0);
        return String.join("|", l);
    }

    /** Header text with the arrow (open = down, closed = right). */
    public static String headerText(String title, boolean open) { return (open ? "\u25BC  " : "\u25B6  ") + (title == null ? "" : title.toUpperCase(java.util.Locale.ROOT)); }
}
