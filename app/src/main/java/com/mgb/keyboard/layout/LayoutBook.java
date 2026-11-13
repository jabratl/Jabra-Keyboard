package com.mgb.keyboard.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.17.13 (K9) several NAMED layouts + the name of the active one (pure Java, no Android, tested by tools/LayoutBookTest.java).
 * Immutable: every change returns a NEW book; a refused change returns the SAME object (so callers can test "nb == b").
 * Limits: at most MAX_LAYOUTS layouts, names up to MAX_NAME code points, names unique (case-insensitive), no tabs / line breaks in a name.
 * Storage text (encode / decode) = "BOOK1", "A<TAB>active", "E<TAB>name<TAB>layout text" with % escapes (%25 %0A %0D %09).
 * Backup pack (pack / unpack) = "JLP1" + "key<TAB>value" lines of the few keys of the prefs file jabra_layouts.
 */
public final class LayoutBook {
    public static final int MAX_LAYOUTS = 12, MAX_NAME = 24, MAX_TEXT = 200000;
    private static final String HEAD = "BOOK1", PACK_HEAD = "JLP1";
    /** Keys of prefs file jabra_layouts that go into a backup. */
    public static final String[] PACK_KEYS = { "layout_v1", "book_v1", "active", "edited", "built_v" };

    private final List<String> names;
    private final List<String> texts;
    private final String active;

    private LayoutBook(List<String> n, List<String> t, String a) { names = n; texts = t; active = a == null ? "" : a; }

    public static LayoutBook empty() { return new LayoutBook(new ArrayList<String>(), new ArrayList<String>(), ""); }

    // ---------- names ----------
    /** Cleaned name: control chars / tabs / line breaks become spaces, ends trimmed, at most MAX_NAME code points. "" = unusable. */
    public static String cleanName(String s) {
        if (s == null) return "";
        String t = LayoutText.clean(s, MAX_NAME * 2).replace('\n', ' ').replace('\t', ' ').trim();
        t = LayoutText.clean(t, MAX_NAME).trim();
        return t;
    }

    public int size() { return names.size(); }
    public String name(int i) { return (i >= 0 && i < names.size()) ? names.get(i) : ""; }
    public String active() { return active; }
    public List<String> names() { return new ArrayList<String>(names); }

    public int indexOf(String name) {
        String n = cleanName(name);
        if (n.length() == 0) return -1;
        for (int i = 0; i < names.size(); i++) if (names.get(i).equalsIgnoreCase(n)) return i;
        return -1;
    }

    public boolean has(String name) { return indexOf(name) >= 0; }

    /** Text of the saved layout, or "" when the name is unknown. */
    public String textOf(String name) { int i = indexOf(name); return i < 0 ? "" : texts.get(i); }

    /** First free name: base, then "base 2", "base 3" ... (cut so it stays within MAX_NAME). */
    public String uniqueName(String base) {
        String b = cleanName(base);
        if (b.length() == 0) b = "Layout";
        if (!has(b)) return b;
        for (int n = 2; n < 1000; n++) {
            String suf = " " + n;
            String stem = b;
            while (LayoutText.cp(stem) + suf.length() > MAX_NAME && stem.length() > 1) stem = stem.substring(0, stem.length() - 1).trim();
            String c = stem + suf;
            if (!has(c)) return c;
        }
        return b;
    }

    // ---------- changes ----------
    /** "" = saveAs(name, text) would work (a new name or an overwrite), else the reason. */
    public String saveProblem(String name, String text) {
        if (cleanName(name).length() == 0) return "type a name";
        if (text == null || text.trim().length() == 0) return "no layout to save";
        if (text.length() > MAX_TEXT) return "layout too big";
        if (!has(name) && names.size() >= MAX_LAYOUTS) return "at most " + MAX_LAYOUTS + " saved layouts, delete one first";
        return "";
    }

    /** Saves [text] under [name] (same name = replaces that entry) and makes it the active name. Refused = this. */
    public LayoutBook saveAs(String name, String text) {
        if (saveProblem(name, text).length() > 0) return this;
        String n = cleanName(name);
        List<String> ns = new ArrayList<String>(names), ts = new ArrayList<String>(texts);
        int i = indexOf(n);
        if (i >= 0) { ts.set(i, text); n = ns.get(i); } else { ns.add(n); ts.add(text); }
        return new LayoutBook(ns, ts, n);
    }

    /** Adds [text] under [name] WITHOUT changing the active name (import). Same name = replaces. Refused = this. */
    public LayoutBook add(String name, String text) {
        if (saveProblem(name, text).length() > 0) return this;
        String n = cleanName(name);
        List<String> ns = new ArrayList<String>(names), ts = new ArrayList<String>(texts);
        int i = indexOf(n);
        if (i >= 0) ts.set(i, text); else { ns.add(n); ts.add(text); }
        return new LayoutBook(ns, ts, active);
    }

    public LayoutBook setActive(String name) {
        int i = indexOf(name);
        if (i < 0) return this;
        return new LayoutBook(new ArrayList<String>(names), new ArrayList<String>(texts), names.get(i));
    }

    /** New name must be free (case-only change of the same entry is fine). The active name follows. Refused = this. */
    public LayoutBook rename(String oldName, String newName) {
        int i = indexOf(oldName);
        String n = cleanName(newName);
        if (i < 0 || n.length() == 0) return this;
        int j = indexOf(n);
        if (j >= 0 && j != i) return this;
        if (names.get(i).equals(n)) return this;
        List<String> ns = new ArrayList<String>(names);
        boolean wasActive = names.get(i).equalsIgnoreCase(active);
        ns.set(i, n);
        return new LayoutBook(ns, new ArrayList<String>(texts), wasActive ? n : active);
    }

    /** Copy named "<name> 2" (first free). Refused (full / unknown) = this. The copy is NOT made active. */
    public LayoutBook duplicate(String name) {
        int i = indexOf(name);
        if (i < 0 || names.size() >= MAX_LAYOUTS) return this;
        String n = uniqueName(names.get(i));
        List<String> ns = new ArrayList<String>(names), ts = new ArrayList<String>(texts);
        ns.add(n); ts.add(texts.get(i));
        return new LayoutBook(ns, ts, active);
    }

    /** Removes the entry; when it was the active one the active name becomes "". */
    public LayoutBook delete(String name) {
        int i = indexOf(name);
        if (i < 0) return this;
        boolean wasActive = names.get(i).equalsIgnoreCase(active);
        List<String> ns = new ArrayList<String>(names), ts = new ArrayList<String>(texts);
        ns.remove(i); ts.remove(i);
        return new LayoutBook(ns, ts, wasActive ? "" : active);
    }

    /** Keeps only entries whose text passes the load gate (restore). */
    public LayoutBook dropInvalid() {
        List<String> ns = new ArrayList<String>(), ts = new ArrayList<String>();
        for (int i = 0; i < names.size(); i++) {
            if (LayoutGuard.gate(texts.get(i)).ok()) { ns.add(names.get(i)); ts.add(texts.get(i)); }
        }
        if (ns.size() == names.size()) return this;
        String a = "";
        for (String n : ns) if (n.equalsIgnoreCase(active)) a = n;
        return new LayoutBook(ns, ts, a);
    }

    // ---------- storage text ----------
    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%') b.append("%25");
            else if (c == '\n') b.append("%0A");
            else if (c == '\r') b.append("%0D");
            else if (c == '\t') b.append("%09");
            else b.append(c);
        }
        return b.toString();
    }

    public static String unesc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                String h = s.substring(i + 1, i + 3);
                if (h.equals("25")) { b.append('%'); i += 2; continue; }
                if (h.equals("0A")) { b.append('\n'); i += 2; continue; }
                if (h.equals("0D")) { b.append('\r'); i += 2; continue; }
                if (h.equals("09")) { b.append('\t'); i += 2; continue; }
            }
            b.append(c);
        }
        return b.toString();
    }

    public String encode() {
        StringBuilder b = new StringBuilder(HEAD).append('\n');
        b.append("A\t").append(esc(active)).append('\n');
        for (int i = 0; i < names.size(); i++) b.append("E\t").append(esc(names.get(i))).append('\t').append(esc(texts.get(i))).append('\n');
        return b.toString();
    }

    /** Never null and never throws: bad / unknown text = an empty book; bad lines are skipped; limits are enforced. */
    public static LayoutBook decode(String s) {
        try {
            if (s == null || s.length() == 0) return empty();
            String[] lines = s.split("\n", -1);
            if (lines.length == 0 || !lines[0].equals(HEAD)) return empty();
            LayoutBook b = empty();
            String act = "";
            for (int i = 1; i < lines.length; i++) {
                String ln = lines[i];
                if (ln.startsWith("A\t")) { act = unesc(ln.substring(2)); continue; }
                if (!ln.startsWith("E\t")) continue;
                int t = ln.indexOf('\t', 2);
                if (t < 0) continue;
                String name = unesc(ln.substring(2, t)), text = unesc(ln.substring(t + 1));
                if (!cleanName(name).equals(name)) continue;      // names are stored already cleaned
                if (b.has(name) || b.saveProblem(name, text).length() > 0) continue;
                b = b.add(name, text);
            }
            return b.setActive(act);
        } catch (Throwable t) { return empty(); }
    }

    // ---------- backup pack ----------
    /** Pack of the jabra_layouts keys (only PACK_KEYS, null values skipped). */
    public static String pack(Map<String, String> kv) {
        StringBuilder b = new StringBuilder(PACK_HEAD).append('\n');
        if (kv != null) for (String k : PACK_KEYS) {
            String v = kv.get(k);
            if (v != null) b.append(k).append('\t').append(esc(v)).append('\n');
        }
        return b.toString();
    }

    /** Back to the map (only PACK_KEYS); null = not a pack. */
    public static Map<String, String> unpack(String s) {
        try {
            if (s == null) return null;
            String[] lines = s.split("\n", -1);
            if (lines.length == 0 || !lines[0].equals(PACK_HEAD)) return null;
            Map<String, String> m = new LinkedHashMap<String, String>();
            for (int i = 1; i < lines.length; i++) {
                int t = lines[i].indexOf('\t');
                if (t <= 0) continue;
                String k = lines[i].substring(0, t);
                boolean known = false;
                for (String p : PACK_KEYS) if (p.equals(k)) known = true;
                if (known) m.put(k, unesc(lines[i].substring(t + 1)));
            }
            return m;
        } catch (Throwable t) { return null; }
    }
}
