package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.16.11 (plan X, step X11 part 2b): static list for the in-keyboard settings search (NEW gear panel + More page, panel_ui = 1).
 * The IME cannot use the app's settings screen code, so this is a small pure list: title + group + route (+ extra keywords, Bangla too).
 * route = panel name for host.showPanel(route): a PanelTiles route or "more" / "settings". Search = SettingsIndex rules, max PanelTiles.SEARCH_MAX hits.
 * No Android, nothing stored. Tested by tools/PanelSearchDataTest.java.
 */
public final class PanelSearchData {
    private PanelSearchData() { }

    public static final class Item {
        public final String title, group, route, words;
        Item(String title, String group, String route, String words) {
            this.title = title; this.group = group; this.route = route; this.words = words == null ? "" : words;
        }
    }

    /** Routes that are valid besides the PanelTiles routes. */
    public static final String[] EXTRA_ROUTES = { "more", "settings" };

    private static final Item[] ALL = {
        // Size panel
        new Item("Key height (portrait)", "Size", "size", "height tall short taller shorter উচ্চতা"),
        new Item("Key height (landscape)", "Size", "size", "height tall short wide"),
        new Item("Key gap", "Size", "size", "space between keys spacing"),
        new Item("Letter size", "Size", "size", "font text bigger smaller অক্ষর"),
        new Item("Number row height", "Size", "size", "height digits"),
        new Item("Large number row", "Size", "size", "digits big"),
        new Item("Row gap (up-down)", "Size", "size", "vertical space"),
        new Item("Key corner radius", "Size", "size", "round corners shape"),
        new Item("Edge distance", "Size", "size", "margin side"),
        new Item("One-hand width", "Size", "size", "narrow small hand"),
        new Item("Same keyboard height", "Size", "size", "height lock fixed"),
        // Languages / key map
        new Item("Bangla layout", "Languages", "keymap", "bengali bangla বাংলা"),
        new Item("Avro key map", "Languages", "keymap", "phonetic bangla avro"),
        new Item("Probhat key map", "Languages", "keymap", "probhat fixed layout"),
        new Item("Language key tap", "Languages", "keymap", "switch language globe"),
        new Item("Space swipe = language", "Languages", "keymap", "swipe spacebar switch"),
        new Item("Show language key", "Languages", "keymap", "EN key"),
        // Typing panel
        new Item("Double-space period", "Typing", "typing", "full stop dot space"),
        new Item("Auto-correct", "Typing", "typing", "spelling fix words"),
        new Item("Learn my words", "Typing", "typing", "dictionary learn suggestions"),
        new Item("Suggestion row", "Typing", "typing", "suggest words bar"),
        new Item("Long-press characters", "Typing", "typing", "hold key hint extra letters"),
        new Item("Selection quick actions", "Typing", "typing", "copy paste select menu"),
        new Item("Auto-profile per app", "Typing", "typing", "app profile automatic"),
        new Item("Vibrate", "Typing", "typing", "haptic buzz vibration feedback"),
        new Item("Sound", "Typing", "typing", "click tone feedback"),
        new Item("Key popup", "Typing", "typing", "bubble preview"),
        new Item("Formatter", "Typing", "format", "bold italic text style Aa"),
        // More page
        new Item("Hold backspace", "Typing", "more", "delete repeat"),
        new Item("Long hold deletes words", "Typing", "more", "word delete backspace"),
        new Item("Auto caps", "Typing", "more", "capital letter sentence"),
        new Item("Enter action", "Typing", "more", "return send go search"),
        new Item("PC mode", "PC mode", "more", "esc tab ctrl alt arrows function keys"),
        new Item("Mode key", "PC mode", "more", "pc phone switch"),
        new Item("Remember mode", "PC mode", "more", "pc phone keep"),
        new Item("One-hand mode", "Look", "more", "left right hand small"),
        new Item("Key border", "Look", "more", "borderless outline"),
        new Item("Look (Classic / Jabra)", "Look", "more", "style"),
        // Look
        new Item("Themes", "Look", "themes", "colour color dark light"),
        new Item("Theme studio", "Look", "tstudio", "custom theme colours create"),
        new Item("Emoji panel", "Look", "emoji", "smileys kaomoji"),
        // Toolbar
        new Item("Toolbar editor", "Toolbar", "toolbar", "order hide show tools icons"),
        new Item("Quick toolbar", "Toolbar", "tbquick", "shortcut bar tools"),
        // Clipboard / vault
        new Item("Clip history", "Clipboard", "clipboard", "copied text remember"),
        new Item("Clipboard", "Clipboard", "clipboard", "copy paste pin"),
        new Item("Vault", "Clipboard", "vault", "snippets notes templates glossary"),
        // Tools / data
        new Item("Text tools", "Tools", "tt", "uppercase lowercase convert"),
        new Item("Cursor / select pad", "Tools", "pad", "move cursor select arrows"),
        new Item("Profiles", "Data", "profiles", "backup restore save export import"),
        new Item("More settings", "Typing", "more", "extra page"),
        new Item("Settings", "Typing", "settings", "gear home"),
    };

    public static int count() { return ALL.length; }

    /** Copy of the full list (display order). */
    public static List<Item> all() {
        List<Item> out = new ArrayList<>();
        for (Item i : ALL) out.add(i);
        return out;
    }

    /** True when the route can be opened with host.showPanel (a PanelTiles route, "more" or "settings"). */
    public static boolean isKnownRoute(String route) {
        if (route == null || route.isEmpty()) return false;
        if (PanelTiles.byRoute(route) != null) return true;
        for (String r : EXTRA_ROUTES) if (r.equals(route)) return true;
        return false;
    }

    /** Search: blank / null = empty list; at most PanelTiles.SEARCH_MAX items; same word rules as SettingsIndex.search. */
    public static List<Item> search(String query) {
        List<Item> out = new ArrayList<>();
        if (!PanelTiles.isSearching(query)) return out;
        List<SettingsIndex.Entry> entries = new ArrayList<>();
        for (int i = 0; i < ALL.length; i++) entries.add(new SettingsIndex.Entry(String.valueOf(i), ALL[i].title, ALL[i].words, ALL[i].group));
        for (SettingsIndex.Entry e : PanelTiles.search(entries, query)) {
            try { out.add(ALL[Integer.parseInt(e.key)]); } catch (RuntimeException ex) { /* skip */ }
        }
        return out;
    }
}
