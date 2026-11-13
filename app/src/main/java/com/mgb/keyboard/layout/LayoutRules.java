package com.mgb.keyboard.layout;

import com.mgb.keyboard.typing.AppProfileMap;
import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.17 (K13) layout RULES: which SAVED layout (LayoutBook name) the keyboard uses for a language, for portrait / landscape, or for one app.
 * Pure Java (no Android), tested by tools/LayoutRulesTest.java. Immutable: a change returns a NEW object, a refused change returns the SAME object.
 * Rule = kind + key + saved layout name. kinds: "lang" (key en | av | pb = the layer of the current language), "orient" (key p | l), "app" (key = Android package name).
 * Priority when resolving: app, then language, then orientation. No rule matches = "" = the active layout is used (= today's behaviour).
 * Storage text "RULES1" + lines "R<TAB>kind<TAB>key<TAB>name" (LayoutBook % escapes); decode never throws, bad lines are skipped.
 */
public final class LayoutRules {
    public static final int MAX_RULES = 40;
    public static final String HEAD = "RULES1";
    public static final String LANG = "lang", ORIENT = "orient", APP = "app";

    private final List<String[]> rules;   // {kind, key, name}

    private LayoutRules(List<String[]> r) { rules = r; }

    public static LayoutRules empty() { return new LayoutRules(new ArrayList<String[]>()); }

    public static boolean validKind(String k) { return LANG.equals(k) || ORIENT.equals(k) || APP.equals(k); }

    public static boolean validKey(String kind, String key) {
        if (key == null || kind == null) return false;
        if (LANG.equals(kind)) return key.equals("en") || key.equals("av") || key.equals("pb");
        if (ORIENT.equals(kind)) return key.equals("p") || key.equals("l");
        if (APP.equals(kind)) return AppProfileMap.validPackage(key);
        return false;
    }

    public int size() { return rules.size(); }
    public boolean isEmpty() { return rules.isEmpty(); }
    public String kind(int i) { return (i >= 0 && i < rules.size()) ? rules.get(i)[0] : ""; }
    public String key(int i) { return (i >= 0 && i < rules.size()) ? rules.get(i)[1] : ""; }
    public String name(int i) { return (i >= 0 && i < rules.size()) ? rules.get(i)[2] : ""; }

    private int find(String kind, String key) {
        for (int i = 0; i < rules.size(); i++) if (rules.get(i)[0].equals(kind) && rules.get(i)[1].equals(key)) return i;
        return -1;
    }

    /** Saved layout name of one rule, or "" when there is none. */
    public String nameFor(String kind, String key) { int i = find(kind, key); return i < 0 ? "" : rules.get(i)[2]; }

    /** Adds or replaces one rule. Refused (bad kind / key / name, or more than MAX_RULES) = this. */
    public LayoutRules put(String kind, String key, String layoutName) {
        String n = LayoutBook.cleanName(layoutName);
        if (!validKey(kind, key) || n.length() == 0) return this;
        int i = find(kind, key);
        if (i < 0 && rules.size() >= MAX_RULES) return this;
        List<String[]> r = new ArrayList<String[]>(rules);
        String[] e = new String[] { kind, key, n };
        if (i >= 0) r.set(i, e); else r.add(e);
        return new LayoutRules(r);
    }

    /** Removes one rule (no such rule = this). */
    public LayoutRules remove(String kind, String key) {
        int i = find(kind, key);
        if (i < 0) return this;
        List<String[]> r = new ArrayList<String[]>(rules);
        r.remove(i);
        return new LayoutRules(r);
    }

    public LayoutRules clear() { return rules.isEmpty() ? this : empty(); }

    /** Which saved layout NAME applies now: app rule, else language rule, else orientation rule, else "" (= use the active layout). */
    public String resolve(String pkg, String layer, boolean land) {
        String n = "";
        if (pkg != null && pkg.length() > 0) n = nameFor(APP, pkg);
        if (n.length() == 0 && layer != null) n = nameFor(LANG, layer);
        if (n.length() == 0) n = nameFor(ORIENT, land ? "l" : "p");
        return n;
    }

    /** Drops rules whose saved layout no longer exists in [book] (names compared case-insensitively, kept in the book's spelling). Nothing to drop = this. */
    public LayoutRules dropMissing(LayoutBook book) {
        if (book == null) return empty();
        List<String[]> r = new ArrayList<String[]>();
        boolean changed = false;
        for (String[] e : rules) {
            int bi = book.indexOf(e[2]);
            if (bi < 0) { changed = true; continue; }
            String real = book.name(bi);
            if (!real.equals(e[2])) changed = true;
            r.add(new String[] { e[0], e[1], real });
        }
        return changed ? new LayoutRules(r) : this;
    }

    /** True when the keyboard must be rebuilt: the layout name asked for now differs from the one the keyboard was built with. */
    public static boolean changed(String built, String now) {
        String a = built == null ? "" : built, b = now == null ? "" : now;
        return !a.equals(b);
    }

    // ---------- texts for the editor ----------
    public static String keyTitle(String kind, String key) {
        if (LANG.equals(kind)) return key.equals("en") ? "English" : key.equals("av") ? "Bangla (Avro)" : key.equals("pb") ? "Bangla (Probhat)" : key;
        if (ORIENT.equals(kind)) return key.equals("l") ? "Landscape" : "Portrait";
        return key;
    }

    public static String kindTitle(String kind) { return LANG.equals(kind) ? "Language" : ORIENT.equals(kind) ? "Screen" : "App"; }

    /** One readable line, e.g. "Language English -> Coder". */
    public String line(int i) {
        if (i < 0 || i >= rules.size()) return "";
        String[] e = rules.get(i);
        return kindTitle(e[0]) + " " + keyTitle(e[0], e[1]) + " -> " + e[2];
    }

    // ---------- storage ----------
    public String encode() {
        StringBuilder b = new StringBuilder(HEAD).append('\n');
        for (String[] e : rules) b.append("R\t").append(e[0]).append('\t').append(e[1]).append('\t').append(LayoutBook.esc(e[2])).append('\n');
        return b.toString();
    }

    /** Never null, never throws: bad text = no rules; bad lines, duplicates and rules beyond MAX_RULES are skipped. */
    public static LayoutRules decode(String s) {
        try {
            if (s == null || s.length() == 0 || s.length() > 20000) return empty();
            String[] lines = s.split("\n", -1);
            if (lines.length == 0 || !lines[0].equals(HEAD)) return empty();
            LayoutRules r = empty();
            for (int i = 1; i < lines.length; i++) {
                String[] p = lines[i].split("\t", -1);
                if (p.length != 4 || !p[0].equals("R")) continue;
                String name = LayoutBook.unesc(p[3]);
                if (!LayoutBook.cleanName(name).equals(name)) continue;
                if (r.find(p[1], p[2]) >= 0) continue;
                r = r.put(p[1], p[2], name);
            }
            return r;
        } catch (Throwable t) { return empty(); }
    }
}
