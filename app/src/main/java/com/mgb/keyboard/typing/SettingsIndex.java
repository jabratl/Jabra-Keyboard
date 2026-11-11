package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.4.2 pure Java helpers for the settings screen (no Android classes, tested by tools/SettingsIndexTest.java).
 * - search(entries, query): every word of the query must appear in title + help + group + key (case-insensitive).
 * - recent list: csv of pref keys, newest first, no duplicates, max 8 (pref set_recent).
 * - last change: "key|oldValue" (pref set_last), one level of undo. Old value is an int (toggle 0/1, level / cycle index).
 * - help(key): one short grey line shown under a row. Unknown key = "" (no line).
 */
public final class SettingsIndex {
    private SettingsIndex() {}

    public static final int RECENT_MAX = 8;

    public static final class Entry {
        public final String key, title, help, group;
        public Entry(String key, String title, String help, String group) {
            this.key = key == null ? "" : key;
            this.title = title == null ? "" : title;
            this.help = help == null ? "" : help;
            this.group = group == null ? "" : group;
        }
    }

    private static String low(String s) { return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT); }

    public static boolean matches(Entry e, String query) {
        if (e == null) return false;
        String q = low(query).trim();
        if (q.isEmpty()) return true;
        String hay = low(e.title) + " " + low(e.help) + " " + low(e.group) + " " + low(e.key).replace('_', ' ');
        for (String w : q.split("\\s+")) {
            if (w.isEmpty()) continue;
            if (!hay.contains(w)) return false;
        }
        return true;
    }

    public static List<Entry> search(List<Entry> all, String query) {
        List<Entry> out = new ArrayList<>();
        if (all == null) return out;
        for (Entry e : all) if (matches(e, query)) out.add(e);
        return out;
    }

    public static List<String> parseRecent(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String p : csv.split(",")) {
            String k = p.trim();
            if (k.isEmpty() || out.contains(k)) continue;
            out.add(k);
            if (out.size() >= RECENT_MAX) break;
        }
        return out;
    }

    public static String pushRecent(String csv, String key) {
        if (key == null || key.trim().isEmpty() || key.indexOf(',') >= 0 || key.indexOf('|') >= 0) return csv == null ? "" : csv;
        List<String> cur = parseRecent(csv);
        cur.remove(key);
        cur.add(0, key);
        while (cur.size() > RECENT_MAX) cur.remove(cur.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cur.size(); i++) { if (i > 0) sb.append(','); sb.append(cur.get(i)); }
        return sb.toString();
    }

    public static String encodeLast(String key, int oldValue) {
        if (key == null || key.isEmpty() || key.indexOf('|') >= 0) return "";
        return key + "|" + oldValue;
    }

    /** Returns {key, oldValue as text} or null when empty / broken. */
    public static String[] parseLast(String s) {
        if (s == null) return null;
        int i = s.lastIndexOf('|');
        if (i <= 0 || i >= s.length() - 1) return null;
        String k = s.substring(0, i);
        String v = s.substring(i + 1);
        try { Integer.parseInt(v); } catch (NumberFormatException ex) { return null; }
        return new String[] { k, v };
    }

    public static String help(String key) {
        if (key == null) return "";
        switch (key) {
            case "bn_layout": return "Which Bangla keyboard the language key uses: Avro (phonetic) or Probhat (fixed).";
            case "lang_tap": return "Language key tap: EN <-> Bangla (default), or cycle through all languages.";
            case "lang_fav": return "Which Bangla layout opens first when you tap the language key from English.";
            case "lang_avro_on": return "OFF hides Avro from the language key (English always stays).";
            case "lang_probhat_on": return "OFF hides Probhat from the language key (English always stays).";
            case "numrow": return "Shows a row of digits above the letters.";
            case "numpad_auto": return "Opens the number pad by itself in number and phone fields.";
            case "pc_mode": return "Phone = normal keys. PC = extra Esc / Ctrl / F-keys / arrows rows.";
            case "mode_key": return "Shows the Phone / PC switch key in the edit bar.";
            case "pc_remember": return "OFF = the keyboard always opens in Phone mode.";
            case "bs_repeat": return "Holding backspace keeps deleting.";
            case "bs_word": return "A long hold on backspace deletes whole words.";
            case "autocap": return "Starts a sentence with a capital letter.";
            case "enter_action": return "Enter does Go / Search / Send when the app asks for it.";
            case "ds_period": return "Two quick spaces type a full stop and a space.";
            case "ds_tab": return "Two quick spaces type a tab (when the period rule does not apply).";
            case "hint_on": return "Long-press a letter to type its small hint character.";
            case "theme": return "Colour theme of the keyboard (picker is inside the keyboard).";
            case "look": return "Classic = old square keys. Jabra = round keys, pill space key.";
            case "key_border": return "Draws or removes the border around keys.";
            case "onehand": return "Moves the keyboard to the left or right side.";
            case "icons_on": return "Own drawn icons on function keys. OFF = text labels.";
            case "numrow_large": return "Makes the number row a bit taller.";
            case "space_lang": return "Swipe left / right on the space key to switch language at once. Cursor drag on space is off while this is ON.";
            case "sp_preview": return "Shows the active language in a bubble while you swipe on space. Needs Space swipe ON.";
            case "key_lang": return "Hide the language (EN) key. Works only while Space swipe is ON (else the key stays).";
            case "tb_tip": return "Hold a toolbar icon for 0.3 s to see its name above it.";
            case "numrow_bn": return "Avro / Probhat show Bangla digits on the number row; hold a key for the Latin digit.";
            case "hint_digits": return "Number row shows a small Bangla digit; hold the key to type it.";
            case "hint_size_lv": return "Size of the small hint characters (Normal = as before).";
            case "hint_alpha_lv": return "How strong the small hint characters look (Normal = as before).";
            case "hint_hide": return "Hides the small hint letters; long-press still works.";
            case "fb_down": return "Vibration / sound the moment your finger touches a letter or number key (not when you lift). Backspace, space and Shift keep their own timing.";
            case "haptic_native": return "Uses the phone's own key-tap vibration (strength level is ignored). OFF = the Vibration strength setting.";
            case "haptic": return "Short vibration on every key press.";
            case "sound": return "Click sound on every key press.";
            case "key_popup": return "Shows a bubble with the letter above the pressed key.";
            case "pop_ref": return "Bigger rounded key bubble in your theme colour (needs Key popup ON). OFF = old look.";   // v0.9.6
            case "sg_on": return "Word suggestions in the top row; the left icon opens the toolbar. OFF = toolbar only.";   // v0.10.1
            case "pop_fast": return "Draws the key bubble inside the keyboard: smoother (needs Key popup ON). OFF = old.";   // v0.9.9
            case "sp_tick": return "Soft tick for every language step while you swipe on space. Needs Space swipe ON.";   // v0.9.8
            case "sp_step": return "How far to swipe on space for one language step: Small, Normal or Large.";   // v0.9.8
            case "h_lock": return "Keyboard keeps the same height in English, Avro and Probhat (no jump). OFF = old.";   // v0.9.7
            case "key_globe": return "Shows a key to switch to the next keyboard.";
            case "key_voice": return "Shows a key that opens the phone's voice typing.";
            case "key_emoji": return "Shows an emoji key next to the language key.";
            case "lp_delay": return "How long you must hold a key for a long-press.";
            case "cursor_speed": return "How fast the cursor moves when you drag on the space key.";
            case "edit_bar": return "The toolbar row above the letters (Aa, clipboard, select, copy...).";
            case "clip_on": return "Saves copied text in the clipboard history.";
            case "fmt_on": return "Shows the Aa novel formatter button.";
            case "clip_lines": return "How many lines of each clip are shown in the clipboard.";
            case "clip_view": return "Clipboard as a list or as 2-column cards.";
            case "clip_sort": return "Order of clips in the clipboard: Newest, Oldest, A-Z or Most used.";
            case "clip_count": return "Shows the number of characters (and paste count) on each clip.";
            case "clip_expire": return "Deletes unpinned clips after this time. Pinned clips are never deleted.";
            case "tb_vault": return "Shows the Vault key on the toolbar (saved texts, terms and templates).";   // v0.7.1
            default: return "";
        }
    }
}
