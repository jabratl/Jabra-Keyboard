package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * v0.15.0 (M5 of the "App UI" plan): pure rules for the personal Home. No Android classes.
 *  - Favourites: a short list of setting keys pinned on Home (pref fav_keys, comma list). Unknown keys are dropped, max MAX_FAVS.
 *  - Tiles: own order (pref tile_order, comma list of page ids) and hidden tiles (pref tile_hide). Missing pages are added in the
 *    default order, unknown ids dropped. Data & About can never be hidden (it holds the old-screen switch, backup, update), so Home is never empty.
 * A hidden tile only leaves Home: its settings stay reachable by search.
 */
public final class PersonalPlan {
    private PersonalPlan() { }

    public static final int MAX_FAVS = 8;
    public static final String PREF_FAVS = "fav_keys", PREF_ORDER = "tile_order", PREF_HIDE = "tile_hide";

    /** Comma list -> trimmed, non-empty, no duplicates, same order. null = empty. */
    public static List<String> parseList(String raw) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        if (raw != null) for (String p : raw.split(",")) { String t = p.trim(); if (!t.isEmpty()) s.add(t); }
        return new ArrayList<>(s);
    }

    public static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(','); b.append(s); }
        return b.toString();
    }

    // ---- favourites ----

    /** The favourites that still exist (in [validKeys]), saved order, at most MAX_FAVS. */
    public static List<String> favs(String raw, Collection<String> validKeys) {
        List<String> out = new ArrayList<>();
        for (String k : parseList(raw)) {
            if (validKeys != null && validKeys.contains(k)) { out.add(k); if (out.size() >= MAX_FAVS) break; }
        }
        return out;
    }

    public static boolean isFav(String raw, String key) { return key != null && parseList(raw).contains(key); }

    /** Pin when not pinned (appended; refused when MAX_FAVS pinned already), unpin when pinned. Unknown key = list unchanged (cleaned). */
    public static String toggleFav(String raw, String key, Collection<String> validKeys) {
        List<String> cur = favs(raw, validKeys);
        if (key == null || validKeys == null || !validKeys.contains(key)) return join(cur);
        if (cur.contains(key)) cur.remove(key);
        else if (cur.size() < MAX_FAVS) cur.add(key);
        return join(cur);
    }

    /** false = a new favourite would be refused (list full). */
    public static boolean canPin(String raw, String key, Collection<String> validKeys) {
        List<String> cur = favs(raw, validKeys);
        return cur.contains(key) || cur.size() < MAX_FAVS;
    }

    // ---- tiles ----

    /** Full tile order (hidden ones included): saved valid ids first (no duplicates), then the missing pages in default order. */
    public static List<String> order(String rawOrder) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        for (String id : parseList(rawOrder)) if (PagePlan.index(id) >= 0) s.add(id);
        for (String id : PagePlan.ORDER) s.add(id);
        return new ArrayList<>(s);
    }

    public static boolean canHide(String id) { return PagePlan.index(id) >= 0 && !PagePlan.DATA.equals(id); }

    /** Hidden page ids (valid, hide-able only). */
    public static List<String> hidden(String rawHide) {
        List<String> out = new ArrayList<>();
        for (String id : parseList(rawHide)) if (canHide(id)) out.add(id);
        return out;
    }

    /** Tiles shown on Home, in order. Never empty (Data & About is always there). */
    public static List<String> visible(String rawOrder, String rawHide) {
        List<String> hid = hidden(rawHide);
        List<String> out = new ArrayList<>();
        for (String id : order(rawOrder)) if (!hid.contains(id)) out.add(id);
        return out;
    }

    /** Moves [id] one place up (dir < 0) or down (dir > 0) in the FULL order; at the border or unknown id = unchanged. Returns the new raw order. */
    public static String move(String rawOrder, String id, int dir) {
        List<String> o = order(rawOrder);
        int i = o.indexOf(id);
        if (i < 0 || dir == 0) return join(o);
        int j = dir < 0 ? i - 1 : i + 1;
        if (j < 0 || j >= o.size()) return join(o);
        String t = o.get(i); o.set(i, o.get(j)); o.set(j, t);
        return join(o);
    }

    /** Hide / show a tile. A page that cannot be hidden = unchanged. Returns the new raw hide list. */
    public static String toggleHide(String rawHide, String id) {
        List<String> h = hidden(rawHide);
        if (!canHide(id)) return join(h);
        if (h.contains(id)) h.remove(id); else h.add(id);
        return join(h);
    }

    /** true when the user changed the order or hid a tile (for the "Reset" button). */
    public static boolean customised(String rawOrder, String rawHide) {
        if (!hidden(rawHide).isEmpty()) return true;
        List<String> o = order(rawOrder);
        for (int i = 0; i < PagePlan.ORDER.length; i++) if (!PagePlan.ORDER[i].equals(o.get(i))) return true;
        return false;
    }

    // ---- texts (one place, so the tests can check them) ----
    public static String favHint() { return "Tip: search a setting and tap the star to pin it here."; }
    public static String favTitle(int n) { return "Favourites (" + n + ")"; }
    public static String pinLabel(String title, boolean pinned) { return (pinned ? "Unpin " : "Pin ") + title + (pinned ? " from Home" : " to Home"); }
    public static String fullNote() { return "Favourites are full (" + MAX_FAVS + "). Unpin one first."; }
}
