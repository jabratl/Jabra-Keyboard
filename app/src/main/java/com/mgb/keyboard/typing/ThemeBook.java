package com.mgb.keyboard.typing;

import com.mgb.keyboard.layout.LayoutBook;
import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.24 (L4) several NAMED custom themes + the name of the one last saved / used (pure Java, no Android, tested by tools/ThemeBookTest.java).
 * A theme text is the JSON of {@link CustomTheme} (the same text as pref ct_json). Copy of the LayoutBook design:
 * immutable, every change returns a NEW book, a refused change returns the SAME object (callers test "nb == b").
 * Limits: at most MAX_THEMES, names up to LayoutBook.MAX_NAME code points, names unique (case-insensitive), no tabs / line breaks.
 * Storage text = "TBOOK1", "A<TAB>active", "E<TAB>name<TAB>json" with the LayoutBook % escapes. Backup text = the same storage text.
 */
public final class ThemeBook {
    public static final int MAX_THEMES = 12, MAX_JSON = 2000, MAX_NAME_CHARS = LayoutBook.MAX_NAME;
    private static final String HEAD = "TBOOK1";

    private final List<String> names;
    private final List<String> texts;
    private final String active;

    private ThemeBook(List<String> n, List<String> t, String a) { names = n; texts = t; active = a == null ? "" : a; }

    public static ThemeBook empty() { return new ThemeBook(new ArrayList<String>(), new ArrayList<String>(), ""); }

    public static String cleanName(String s) { return LayoutBook.cleanName(s); }

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

    /** JSON of the saved theme, or "" when the name is unknown. */
    public String jsonOf(String name) { int i = indexOf(name); return i < 0 ? "" : texts.get(i); }

    /** First free name: base, then "base 2", "base 3" ... (cut so it stays within MAX_NAME). */
    public String uniqueName(String base) {
        String b = cleanName(base);
        if (b.length() == 0) b = "Theme";
        if (!has(b)) return b;
        for (int n = 2; n < 1000; n++) {
            String suf = " " + n;
            String stem = b;
            while (stem.codePointCount(0, stem.length()) + suf.length() > LayoutBook.MAX_NAME && stem.length() > 1) stem = stem.substring(0, stem.length() - 1).trim();
            String c = stem + suf;
            if (!has(c)) return c;
        }
        return b;
    }

    /** "" = saveAs(name, json) would work (a new name or an overwrite), else the reason. */
    public String saveProblem(String name, String json) {
        if (cleanName(name).length() == 0) return "type a name";
        if (json == null || json.trim().length() == 0 || CustomTheme.parse(json) == null) return "no theme to save";
        if (json.length() > MAX_JSON) return "theme too big";
        if (!has(name) && names.size() >= MAX_THEMES) return "at most " + MAX_THEMES + " saved themes, delete one first";
        return "";
    }

    /** Saves [json] under [name] (same name = replaces that entry) and makes it the active name. Refused = this. */
    public ThemeBook saveAs(String name, String json) {
        if (saveProblem(name, json).length() > 0) return this;
        String n = cleanName(name);
        List<String> ns = new ArrayList<String>(names), ts = new ArrayList<String>(texts);
        int i = indexOf(n);
        if (i >= 0) { ts.set(i, json); n = ns.get(i); } else { ns.add(n); ts.add(json); }
        return new ThemeBook(ns, ts, n);
    }

    /** Adds [json] under [name] WITHOUT changing the active name (restore). Same name = replaces. Refused = this. */
    public ThemeBook add(String name, String json) {
        if (saveProblem(name, json).length() > 0) return this;
        String n = cleanName(name);
        List<String> ns = new ArrayList<String>(names), ts = new ArrayList<String>(texts);
        int i = indexOf(n);
        if (i >= 0) ts.set(i, json); else { ns.add(n); ts.add(json); }
        return new ThemeBook(ns, ts, active);
    }

    public ThemeBook setActive(String name) {
        int i = indexOf(name);
        if (i < 0) return this;
        return new ThemeBook(new ArrayList<String>(names), new ArrayList<String>(texts), names.get(i));
    }

    /** Active name becomes "" (used after the custom theme was deleted). */
    public ThemeBook clearActive() {
        if (active.length() == 0) return this;
        return new ThemeBook(new ArrayList<String>(names), new ArrayList<String>(texts), "");
    }

    /** New name must be free (case-only change of the same entry is fine). The active name follows. Refused = this. */
    public ThemeBook rename(String oldName, String newName) {
        int i = indexOf(oldName);
        String n = cleanName(newName);
        if (i < 0 || n.length() == 0) return this;
        int j = indexOf(n);
        if (j >= 0 && j != i) return this;
        if (names.get(i).equals(n)) return this;
        List<String> ns = new ArrayList<String>(names);
        boolean wasActive = names.get(i).equalsIgnoreCase(active);
        ns.set(i, n);
        return new ThemeBook(ns, new ArrayList<String>(texts), wasActive ? n : active);
    }

    /** Copy named "<name> 2" (first free). Refused (full / unknown) = this. The copy is NOT made active. */
    public ThemeBook duplicate(String name) {
        int i = indexOf(name);
        if (i < 0 || names.size() >= MAX_THEMES) return this;
        String n = uniqueName(names.get(i));
        List<String> ns = new ArrayList<String>(names), ts = new ArrayList<String>(texts);
        ns.add(n); ts.add(texts.get(i));
        return new ThemeBook(ns, ts, active);
    }

    /** Removes the entry; when it was the active one the active name becomes "". */
    public ThemeBook delete(String name) {
        int i = indexOf(name);
        if (i < 0) return this;
        boolean wasActive = names.get(i).equalsIgnoreCase(active);
        List<String> ns = new ArrayList<String>(names), ts = new ArrayList<String>(texts);
        ns.remove(i); ts.remove(i);
        return new ThemeBook(ns, ts, wasActive ? "" : active);
    }

    /** Keeps only entries whose JSON is a valid custom theme (restore gate). */
    public ThemeBook dropInvalid() {
        List<String> ns = new ArrayList<String>(), ts = new ArrayList<String>();
        for (int i = 0; i < names.size(); i++) {
            if (CustomTheme.parse(texts.get(i)) != null && texts.get(i).length() <= MAX_JSON) { ns.add(names.get(i)); ts.add(texts.get(i)); }
        }
        if (ns.size() == names.size()) return this;
        String a = "";
        for (String n : ns) if (n.equalsIgnoreCase(active)) a = n;
        return new ThemeBook(ns, ts, a);
    }

    /** True when the saved theme [name] has exactly this JSON (used for the "saved" / "unsaved changes" line). */
    public boolean sameAs(String name, String json) {
        int i = indexOf(name);
        return i >= 0 && json != null && texts.get(i).equals(json);
    }

    // ---------- storage text ----------
    public String encode() {
        StringBuilder b = new StringBuilder(HEAD).append('\n');
        b.append("A\t").append(LayoutBook.esc(active)).append('\n');
        for (int i = 0; i < names.size(); i++) b.append("E\t").append(LayoutBook.esc(names.get(i))).append('\t').append(LayoutBook.esc(texts.get(i))).append('\n');
        return b.toString();
    }

    /** Never null and never throws: bad / unknown text = an empty book; bad lines are skipped; limits are enforced. */
    public static ThemeBook decode(String s) {
        try {
            if (s == null || s.length() == 0) return empty();
            String[] lines = s.split("\n", -1);
            if (lines.length == 0 || !lines[0].equals(HEAD)) return empty();
            ThemeBook b = empty();
            String act = "";
            for (int i = 1; i < lines.length; i++) {
                String ln = lines[i];
                if (ln.startsWith("A\t")) { act = LayoutBook.unesc(ln.substring(2)); continue; }
                if (!ln.startsWith("E\t")) continue;
                int t = ln.indexOf('\t', 2);
                if (t < 0) continue;
                String name = LayoutBook.unesc(ln.substring(2, t)), json = LayoutBook.unesc(ln.substring(t + 1));
                if (!cleanName(name).equals(name)) continue;
                if (b.has(name) || b.saveProblem(name, json).length() > 0) continue;
                b = b.add(name, json);
            }
            return b.setActive(act);
        } catch (Throwable t) { return empty(); }
    }
}
