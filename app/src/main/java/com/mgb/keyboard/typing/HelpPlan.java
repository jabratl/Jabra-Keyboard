package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.78 (U3): pure rules for the "smart hints" (the grey help text of a settings row). No Android. Tested by tools/HelpPlanTest.java.
 * Mode (pref app_tips): SMART (default) = help hides behind an (i) icon and opens by itself ONLY when it matters; ALWAYS = under every row (the old look); OFF = no help, no icon.
 * SMART opens by itself: the first time a flagged (new / risky) row is seen, and when the row is a search hit. Seen rows are remembered in pref help_seen (max 300 keys).
 * Help longer than 90 letters shows a short form first (handwritten in shortOf, or cut at a word); a tap on the text shows the full help.
 */
public final class HelpPlan {
    private HelpPlan() { }

    public static final int SMART = 0, ALWAYS = 1, OFF = 2;
    public static final String PREF_TIPS = "app_tips", PREF_SEEN = "help_seen";
    public static final String[] NAMES = { "Smart", "Always", "Off" };
    public static final int LIMIT = 90, SEEN_MAX = 300;

    /** Rows that can lock the user out of something or change the typing a lot: their help opens once by itself. Keys that do not exist are ignored. */
    public static final String[] FLAGGED = { "lang_en_on", "lang_avro_on", "lang_probhat_on", "space_lang", "key_lang", "bn_design", "pc_chips", "clip_new_look" };

    public static int sanitize(int mode) { return (mode == SMART || mode == ALWAYS || mode == OFF) ? mode : SMART; }

    /** The (i) icon is drawn only in SMART. */
    public static boolean showIcon(int mode) { return sanitize(mode) == SMART; }

    /** The help text exists at all (OFF = never). */
    public static boolean helpExists(int mode) { return sanitize(mode) != OFF; }

    /** Help is open when the row appears. */
    public static boolean startsOpen(int mode) { return sanitize(mode) == ALWAYS; }

    public static boolean isFlagged(String key) {
        if (key == null) return false;
        for (String f : FLAGGED) if (f.equals(key)) return true;
        return false;
    }

    /** SMART only: open by itself now? (search hit, or flagged and never seen). */
    public static boolean autoOpen(int mode, String key, String seenRaw, boolean searchHit) {
        if (sanitize(mode) != SMART) return false;
        return searchHit || (isFlagged(key) && !isSeen(seenRaw, key));
    }

    // ---- seen set: "a,b,c" (oldest first) ----
    public static List<String> parseSeen(String raw) {
        List<String> l = new ArrayList<String>();
        if (raw == null) return l;
        for (String s : raw.split(",")) { String t = s.trim(); if (!t.isEmpty() && !l.contains(t)) l.add(t); }
        return l;
    }

    public static boolean isSeen(String raw, String key) { return key != null && parseSeen(raw).contains(key); }

    /** raw + key (no duplicate; the oldest are dropped above SEEN_MAX). A null / empty key changes nothing. */
    public static String addSeen(String raw, String key) {
        List<String> l = parseSeen(raw);
        if (key == null || key.trim().isEmpty() || key.contains(",")) return join(l);
        if (!l.contains(key)) l.add(key);
        while (l.size() > SEEN_MAX) l.remove(0);
        return join(l);
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) b.append(','); b.append(l.get(i)); }
        return b.toString();
    }

    /** Short form of a help text (<= LIMIT): the text itself when short enough, else the handwritten one, else cut at a word + "...". */
    public static String shortOf(String key, String full) {
        if (full == null) return "";
        if (full.length() <= LIMIT) return full;
        String h = handwritten(key);
        if (h != null && h.length() <= LIMIT) return h;
        int cut = full.lastIndexOf(' ', LIMIT - 3);
        if (cut < 20) cut = LIMIT - 3;
        return full.substring(0, cut).trim() + "...";
    }

    /** Handwritten short forms for the rows whose help is longer than LIMIT. null = none. */
    public static String handwritten(String key) {
        if (key == null) return null;
        switch (key) {
            case "lang_en_on": return "OFF = English is skipped by the language key and the space swipe.";
            case "lang_order": return "Order of the languages for the space swipe and the cycle option.";
            case "bn_design": return "Ridmik look = bare signs, shift letters as hints. Original = the old look.";
            case "bn_hints": return "ON = small digits and signs on Bangla (phonetic) keys; hold a key to type it.";
            case "bn_numpage": return "ON = Bengali digits and signs on the number page (?123) in Bangla (phonetic).";
            case "tools_keep": return "ON = the tools row stays on top when you open the ?123 page.";
            case "pc_chips": return "Shows shortcut chips (Ctrl+C, Ctrl+V ...) in the 4th PC row.";
            case "pc_chips_list": return "Up to 5 shortcuts with commas, e.g. Ctrl+A,Alt+F4. Empty = default.";
            case "key_press_anim": return "The key shrinks a little while pressed and springs back.";
            case "hold_edit": return "Hold c = copy, v = paste, x = cut. Your own Long-press override for that letter wins.";
            case "clip_img": return "Copied pictures and screenshots show on top of the clipboard. Tap = paste, hold = delete.";
            case "lp_punct": return "Hold , or . and slide over more symbols. Lift without moving = old long-press.";
            case "space_lang": return "Swipe left / right on space to switch language. Cursor drag on space turns off.";
            case "fb_down": return "Vibration / sound the moment your finger touches a letter or number key.";
            case "haptic_native": return "Uses the phone's own key-tap vibration. OFF = the Vibration strength setting.";
            case "row_gap": return "Home row (a-l) gets half a key of free space on each side. ON = default.";
            case "bot_style": return "Bottom row widths. Ridmik-like: ?123 and Enter the same width. Old = as before.";
            case "pb_home_gap": return "Bangla fixed home row: half-key gap at the left. Needs Home row side gap ON.";
            case "qa_on": return "Select text: the top row shows Copy, Case, Clean and the word count.";
            case "clip_new_look": return "Neater clipboard: title + count, one Add button, a menu for the rest. OFF = old.";
            default: return null;
        }
    }

    /** true = the shown text is a short form of [full] (a tap should offer the rest). */
    public static boolean isShortened(String key, String full) { return full != null && full.length() > LIMIT; }
}
