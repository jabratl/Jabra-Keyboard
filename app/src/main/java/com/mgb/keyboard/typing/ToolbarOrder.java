package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.2.1 Toolbar order + hidden items. Pure Java (no Android classes) so it can be tested on a computer.
 *
 * Item ids are NEVER renamed (they are saved in prefs tb_order / tb_hidden as comma text).
 * Rules: unknown ids are dropped, duplicates dropped, ids missing from the saved text are appended at the end
 * (so items added in later versions appear by themselves), empty / broken text = DEFAULT order.
 */
public final class ToolbarOrder {

    private ToolbarOrder() { }

    /** Default CUSTOM order: Formatter first, then clipboard, then mode, then the edit keys. Later versions append new ids here. */
    public static final String[] DEFAULT = { "fmt", "clip", "mode", "all", "cut", "copy", "paste", "plain",
        "emoji", "numpad", "sym", "gear", "voice", "globe", "theme", "onehand", "size", "pad", "undo", "redo", "vault" };   // v0.4.4: + vault (extra, OFF until turned on); v0.2.2: the last 9 are EXTRAS; v0.2.3: + pad; v0.2.7: + undo, redo

    /** v0.2.2 extra shortcuts: NOT shown until the user turns them on (pref tb_on). The first 8 ids keep the v0.2.1 rule (shown unless in tb_hidden). */
    public static final String[] EXTRAS = { "emoji", "numpad", "sym", "gear", "voice", "globe", "theme", "onehand", "size", "pad", "undo", "redo", "vault" };   // v0.4.4: + vault

    public static boolean isExtra(String id) {
        if (id == null) return false;
        for (String d : EXTRAS) if (d.equals(id)) return true;
        return false;
    }

    /** Ids written in the saved text of tb_on (known extras only, no duplicates). */
    public static List<String> parseOn(String csv) {
        List<String> out = new ArrayList<String>();
        if (csv == null) return out;
        for (String p : csv.split(",")) {
            String id = p.trim();
            if (isExtra(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    public static String setOn(String onCsv, String id, boolean on) {
        List<String> h = parseOn(onCsv);
        if (!isExtra(id)) return serialize(h);
        if (on) { if (!h.contains(id)) h.add(id); } else { h.remove(id); }
        return serialize(h);
    }

    /** True when the item is NOT on the bar (extra: not turned on; normal item: listed in tb_hidden). */
    public static boolean off(String hiddenCsv, String onCsv, String id) {
        if (isExtra(id)) return !parseOn(onCsv).contains(id);
        return parseHidden(hiddenCsv).contains(id);
    }

    public static boolean known(String id) {
        if (id == null) return false;
        for (String d : DEFAULT) if (d.equals(id)) return true;
        return false;
    }

    private static List<String> defaults() {
        List<String> l = new ArrayList<String>();
        for (String d : DEFAULT) l.add(d);
        return l;
    }

    /** Saved text -> full ordered list of ALL known ids (never empty, no duplicates). */
    public static List<String> parse(String csv) {
        List<String> out = new ArrayList<String>();
        if (csv == null || csv.trim().isEmpty()) return defaults();
        for (String p : csv.split(",")) {
            String id = p.trim();
            if (known(id) && !out.contains(id)) out.add(id);
        }
        if (out.isEmpty()) return defaults();
        for (String d : DEFAULT) if (!out.contains(d)) out.add(d);
        return out;
    }

    public static String serialize(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) { if (sb.length() > 0) sb.append(','); sb.append(s); }
        return sb.toString();
    }

    /** Hidden text -> list of known hidden ids (no duplicates). */
    public static List<String> parseHidden(String csv) {
        List<String> out = new ArrayList<String>();
        if (csv == null) return out;
        for (String p : csv.split(",")) {
            String id = p.trim();
            if (known(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    public static boolean isHidden(String hiddenCsv, String id) { return parseHidden(hiddenCsv).contains(id); }

    /** Returns the new hidden text with id hidden (hide = true) or shown (hide = false). */
    public static String setHidden(String hiddenCsv, String id, boolean hide) {
        List<String> h = parseHidden(hiddenCsv);
        if (!known(id)) return serialize(h);
        if (hide) { if (!h.contains(id)) h.add(id); } else { h.remove(id); }
        return serialize(h);
    }

    /** Order without the hidden ids (what the bar shows). */
    public static List<String> visible(String orderCsv, String hiddenCsv, String onCsv) {
        List<String> all = parse(orderCsv);
        List<String> out = new ArrayList<String>();
        for (String id : all) if (!off(hiddenCsv, onCsv, id)) out.add(id);
        return out;
    }

    /** Order changes work on the FULL list (hidden items keep their place). Each returns a new list. */
    public static List<String> moveFirst(List<String> list, String id) {
        List<String> o = new ArrayList<String>(list);
        if (o.remove(id)) o.add(0, id);
        return o;
    }

    public static List<String> up(List<String> list, String id) {
        List<String> o = new ArrayList<String>(list);
        int i = o.indexOf(id);
        if (i > 0) { o.remove(i); o.add(i - 1, id); }
        return o;
    }

    public static List<String> down(List<String> list, String id) {
        List<String> o = new ArrayList<String>(list);
        int i = o.indexOf(id);
        if (i >= 0 && i < o.size() - 1) { o.remove(i); o.add(i + 1, id); }
        return o;
    }
}
