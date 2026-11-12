package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.16.11 (plan X, step X11): pure data for the NEW Gear / More / Settings tile grid (panel_ui = 1).
 * Tile list (grouped, fixed order), route = the SAME old panel name (host.showPanel(route)), and in-keyboard search over SettingsIndex.
 * No Android, nothing stored. Tested by tools/PanelTilesTest.java.
 */
public final class PanelTiles {
    private PanelTiles() { }

    public static final int SEARCH_MAX = 8;   // most results shown under the search box

    public static final String[] GROUPS = { "Languages", "Typing", "Look", "Size", "Toolbar", "Clipboard", "Tools", "Data" };

    public static final class Tile {
        public final String id, label, group, route;   // route = panel name for host.showPanel (every old panel stays reachable)
        public Tile(String id, String label, String group, String route) {
            this.id = id == null ? "" : id; this.label = label == null ? "" : label;
            this.group = group == null ? "" : group; this.route = route == null ? "" : route;
        }
    }

    private static final Tile[] ALL = {
        new Tile("keymap", "Key map", "Languages", "keymap"),
        new Tile("typing", "Typing", "Typing", "typing"),
        new Tile("format", "Formatter", "Typing", "format"),
        new Tile("themes", "Themes", "Look", "themes"),
        new Tile("tstudio", "Theme studio", "Look", "tstudio"),
        new Tile("emoji", "Emoji", "Look", "emoji"),
        new Tile("size", "Size", "Size", "size"),
        new Tile("toolbar", "Toolbar editor", "Toolbar", "toolbar"),
        new Tile("tbquick", "Quick toolbar", "Toolbar", "tbquick"),
        new Tile("clipboard", "Clipboard", "Clipboard", "clipboard"),
        new Tile("vault", "Vault", "Clipboard", "vault"),
        new Tile("tt", "Text tools", "Tools", "tt"),
        new Tile("pad", "Pad", "Tools", "pad"),
        new Tile("profiles", "Profiles", "Data", "profiles"),
    };

    /** All tiles in display order: group order first (GROUPS), then the order above inside a group. */
    public static List<Tile> tiles() {
        List<Tile> out = new ArrayList<>();
        for (String g : GROUPS) for (Tile t : ALL) if (t.group.equals(g)) out.add(t);
        return out;
    }

    /** Tiles of one group (unknown group = empty list). */
    public static List<Tile> tilesOf(String group) {
        List<Tile> out = new ArrayList<>();
        for (Tile t : tiles()) if (t.group.equals(group)) out.add(t);
        return out;
    }

    /** Groups that have at least one tile, in display order. */
    public static List<String> groups() {
        List<String> out = new ArrayList<>();
        for (String g : GROUPS) if (!tilesOf(g).isEmpty()) out.add(g);
        return out;
    }

    /** Tile for a route name, or null. */
    public static Tile byRoute(String route) {
        if (route == null) return null;
        for (Tile t : ALL) if (t.route.equals(route)) return t;
        return null;
    }

    /** Search the settings index: blank query = empty list (the grid is shown instead); at most SEARCH_MAX results. */
    public static List<SettingsIndex.Entry> search(List<SettingsIndex.Entry> all, String query) {
        List<SettingsIndex.Entry> out = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return out;
        for (SettingsIndex.Entry e : SettingsIndex.search(all, query)) {
            if (out.size() >= SEARCH_MAX) break;
            out.add(e);
        }
        return out;
    }

    /** True when the search box has text (grid hidden, results shown). */
    public static boolean isSearching(String query) { return query != null && !query.trim().isEmpty(); }
}
