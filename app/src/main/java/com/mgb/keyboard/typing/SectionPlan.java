package com.mgb.keyboard.typing;

/**
 * v0.17.80 (U5a of the "App UI" plan): pure rules for BASIC / ADVANCED rows. No Android classes.
 * Pref app_adv (bool, default OFF). Pages with a "Show advanced" button: Typing + Look (U5b adds the other pages by extending ADV_PAGES + the two key tables).
 * The SECTION of a row stays single-sourced in ChangedPlan.sectionOf (this class only forwards it, so the two can never drift).
 * A key that is not in the tables is BASIC (default), so no setting is ever hidden by accident.
 * A hidden (advanced) row shows itself when: the switch is ON, OR the row is changed from its default, OR it was opened from search / favourites.
 */
public final class SectionPlan {
    private SectionPlan() { }

    public static final int BASIC = 0, ADVANCED = 1;
    public static final String PREF = "app_adv";

    /** Pages (PagePlan ids) that have the "Show advanced" button. U5b: add the rest. */
    private static final String[] ADV_PAGES = { PagePlan.TYPING, PagePlan.LOOK };

    // Typing page = groups "Languages & layouts" + "Typing"; Look page = group "Look". Every row key of these 3 groups is listed once.
    private static final String[] BASIC_KEYS = {
        "bn_layout", "lang_tap", "lang_en_on", "lang_avro_on", "lang_probhat_on", "space_lang", "key_lang", "numrow", "numpad_auto", "pc_mode", "mode_key",
        "bs_repeat", "autocap", "enter_action", "ds_period", "hint_on",
        "theme", "look", "key_border", "icons_on", "numrow_large", "onehand", "bn_design", "app_tips"
    };
    private static final String[] ADV_KEYS = {
        "bn_map", "lang_fav", "lang_order", "sp_preview", "sp_tick", "sp_anim", "sp_step", "tools_keep", "bn_hints", "bn_numpage",
        "pc_remember", "pc_real_mods", "pc_layer", "pc_frow", "pc_chips", "pc_chips_list",
        "bs_word", "ds_tab", "lp_accents", "lp_punct", "key_press_anim", "hint_custom", "xrow_chars",
        "numrow_bn", "nr_hpct", "nr_mode", "nr_chars", "hint_hide", "hint_digits", "hint_size_lv", "hint_alpha_lv", "tb_tip"
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
}
