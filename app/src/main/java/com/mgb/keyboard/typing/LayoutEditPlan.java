package com.mgb.keyboard.typing;

import com.mgb.keyboard.layout.KeyDef;
import com.mgb.keyboard.layout.LayerDef;
import com.mgb.keyboard.layout.LayoutCheck;
import com.mgb.keyboard.layout.LayoutDef;
import com.mgb.keyboard.layout.LayoutOps;
import com.mgb.keyboard.layout.LayoutText;
import com.mgb.keyboard.layout.RowDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v0.17.8 (K6a) + v0.17.9 (K6b) + v0.17.10 (K7) pure rules of the app layout editor (no Android): which inspector fields a key has, width steps, shown labels,
 * key positions for drawing / touch, spoken description. Tested by tools/LayoutEditPlanTest.java.
 */
public final class LayoutEditPlan {
    private LayoutEditPlan() { }

    public static final int F_LABEL = 1, F_OUT = 2, F_SHIFT = 4, F_LP = 8, F_WIDTH = 16, F_SWIPE = 32;
    public static final float STEP = 0.25f;

    /** Letter layers the editor shows in K6a (symbol pages come with K8). */
    public static final String[] TAB_IDS = { "en", "av", "pb" };

    public static String[] tabTitles() { return new String[] { "English", LangNames.AVRO, LangNames.PROBHAT }; }

    public static String tabId(int i) { return (i >= 0 && i < TAB_IDS.length) ? TAB_IDS[i] : TAB_IDS[0]; }

    /** Inspector fields of a key kind (bit mask). Character key = all; action key / empty spot = width only (K6b adds more). */
    public static int fields(int kind) {
        if (kind == KeyDef.CHAR) return F_LABEL | F_OUT | F_SHIFT | F_LP | F_WIDTH | F_SWIPE;
        if (kind == KeyDef.ACTION || kind == KeyDef.SPACER) return F_WIDTH;
        return 0;
    }

    public static boolean has(int mask, int field) { return (mask & field) != 0; }

    /** Max length (code points) of the text field: F_LABEL, F_OUT / F_SHIFT, F_LP. 0 = unknown field. */
    public static int limit(int field) {
        if (field == F_LABEL) return LayoutText.MAX_LABEL;
        if (field == F_OUT || field == F_SHIFT || field == F_SWIPE) return LayoutText.MAX_OUT;
        if (field == F_LP) return LayoutText.MAX_LP;
        return 0;
    }

    /** Next width after a [-] / [+] tap (dir < 0 / > 0), kept inside KeyDef.W_MIN .. W_MAX and rounded to 0.25. */
    public static float nextWeight(float w, int dir) {
        float cur = (Float.isNaN(w) || Float.isInfinite(w)) ? 1f : w;
        float n = cur + (dir < 0 ? -STEP : (dir > 0 ? STEP : 0f));
        n = LayoutOps.qw(n);
        if (n < KeyDef.W_MIN) n = KeyDef.W_MIN;
        if (n > KeyDef.W_MAX) n = KeyDef.W_MAX;
        return n;
    }

    /** "1", "1.5", "0.25" (no trailing zeros). */
    public static String weightText(float w) {
        if (Float.isNaN(w) || Float.isInfinite(w)) return "1";
        float r = Math.round(w * 100f) / 100f;
        if (r == (int) r) return String.valueOf((int) r);
        String s = String.valueOf(r);
        return s;
    }

    /** Parses typed text of a width: null / bad / out of range = -1 (caller ignores it). */
    public static float parseWeight(String t) {
        if (t == null) return -1f;
        try {
            float v = Float.parseFloat(t.trim().replace(',', '.'));
            if (Float.isNaN(v) || Float.isInfinite(v) || v < KeyDef.W_MIN || v > KeyDef.W_MAX) return -1f;
            return LayoutOps.qw(v);
        } catch (NumberFormatException e) { return -1f; }
    }

    /** Short text of an ACTION key (editor + app preview). */
    public static String actionGlyph(String action) {
        if (action == null) return "";
        switch (action) {
            case "shift": return "\u21E7";
            case "backspace": return "\u232B";
            case "enter": return "\u23CE";
            case "space": return "space";
            case "sym": return "?123";
            case "lang": return "EN";
            case "emoji": return "\u263A";
            case "globe": return "\uD83C\uDF10";
            case "voice": return "\uD83C\uDFA4";
            case "tab": return "Tab";
            case "clip": return "\u2398";
            case "comma": return ",";
            case "period": return ".";
            case "undo": return "\u21B6";
            case "redo": return "\u21B7";
            case "combo": return "Ctrl";
            default: return action;
        }
    }

    /** Text drawn on a key for the shift state (same rule as the keyboard: label, else output; shift: label[1], else upper-case Latin label, else shift output). */
    public static String shownLabel(KeyDef k, boolean shift) {
        if (k == null || k.kind == KeyDef.SPACER) return "";
        if (k.kind == KeyDef.ACTION) return k.label[0].length() > 0 ? k.label[0] : actionGlyph(k.action);
        if (!shift) return k.label[0].length() > 0 ? k.label[0] : k.out[0];
        if (k.label[1].length() > 0) return k.label[1];
        if (k.label[0].length() > 0) return LayoutText.isLatinLetters(k.label[0]) ? k.label[0].toUpperCase() : k.label[0];
        return k.shiftOut();
    }

    /** The key [id] as drawn in [layer] (reference rows resolved), or null. */
    public static KeyDef findKey(LayoutDef m, String layer, String id) {
        if (m == null || id == null) return null;
        for (RowDef r : m.effectiveRows(layer)) for (KeyDef k : r.keys) if (id.equals(k.id)) return k;
        return null;
    }

    /** {row, position} (0-based) of key [id] in the drawn rows of [layer], or null. */
    public static int[] locate(LayoutDef m, String layer, String id) {
        if (m == null || id == null) return null;
        List<RowDef> rows = m.effectiveRows(layer);
        for (int i = 0; i < rows.size(); i++) {
            List<KeyDef> ks = rows.get(i).keys;
            for (int j = 0; j < ks.size(); j++) if (id.equals(ks.get(j).id)) return new int[] { i, j };
        }
        return null;
    }

    /** First character key of the layer (start selection), or "". */
    public static String firstCharId(LayoutDef m, String layer) {
        if (m == null) return "";
        for (RowDef r : m.effectiveRows(layer)) for (KeyDef k : r.keys) if (k.kind == KeyDef.CHAR) return k.id;
        return "";
    }

    /** Spoken text: "key q, row 1, position 3, selected" / "empty spot, row 2, position 1". */
    public static String describe(LayoutDef m, String layer, String id, boolean selected) {
        KeyDef k = findKey(m, layer, id);
        int[] p = locate(m, layer, id);
        if (k == null || p == null) return "";
        String name = k.kind == KeyDef.SPACER ? "empty spot" : "key " + (shownLabel(k, false).length() > 0 ? shownLabel(k, false) : k.id);
        return name + ", row " + (p[0] + 1) + ", position " + (p[1] + 1) + (selected ? ", selected" : "");
    }

    /** Left / right edge of every key of a row for a drawing width: {l0, r0, l1, r1, ...}. Keys share [width] by weight, [gap] between them. */
    public static float[] edges(RowDef r, float width, float gap) {
        if (r == null || r.keys.isEmpty() || !(width > 0f)) return new float[0];
        int n = r.keys.size();
        float g = gap > 0f ? gap : 0f;
        float total = 0f;
        for (KeyDef k : r.keys) total += (k.weight > 0f ? k.weight : KeyDef.W_MIN);
        float usable = width - g * (n - 1);
        if (usable < 0f) usable = 0f;
        float[] e = new float[n * 2];
        float x = 0f;
        for (int i = 0; i < n; i++) {
            float w = usable * (r.keys.get(i).weight > 0f ? r.keys.get(i).weight : KeyDef.W_MIN) / total;
            e[i * 2] = x; e[i * 2 + 1] = x + w;
            x += w + g;
        }
        return e;
    }

    /** Index of the key under x (a touch in a gap goes to the nearest key); -1 when there are no keys. */
    public static int hit(float[] edges, float x) {
        if (edges == null || edges.length < 2) return -1;
        int n = edges.length / 2;
        int best = 0; float bd = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float l = edges[i * 2], rr = edges[i * 2 + 1];
            if (x >= l && x <= rr) return i;
            float d = x < l ? l - x : x - rr;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /** Row index under y for equal-gap rows of [heights]; -1 when outside. */
    public static int rowAt(float[] heights, float gap, float y) {
        if (heights == null || y < 0f) return -1;
        float top = 0f;
        for (int i = 0; i < heights.length; i++) {
            float bottom = top + heights[i];
            if (y <= bottom + gap / 2f) return i;
            top = bottom + gap;
        }
        return -1;
    }

    // ---------- v0.17.9 (K6b): structure editing helpers ----------
    public static final int H_STEP = 10;

    /** Next row height % after [-] / [+] (steps of 10, kept inside RowDef.H_MIN .. H_MAX). */
    public static int nextHeight(int pct, int dir) {
        int n = pct + (dir < 0 ? -H_STEP : (dir > 0 ? H_STEP : 0));
        if (n < RowDef.H_MIN) n = RowDef.H_MIN;
        if (n > RowDef.H_MAX) n = RowDef.H_MAX;
        return n;
    }

    /** {layerId, rowIndex} of the layer that really OWNS the key (a shared number / bottom row belongs to layer nr / bot), or null. */
    public static String[] owner(LayoutDef m, String id) {
        if (m == null || id == null) return null;
        for (LayerDef l : m.layers) {
            for (int i = 0; i < l.rows.size(); i++) {
                RowDef r = l.rows.get(i);
                if (r.isRef()) continue;
                for (KeyDef k : r.keys) if (id.equals(k.id)) return new String[] { l.id, String.valueOf(i) };
            }
        }
        return null;
    }

    /** Raw row index of the key inside [layer]'s own rows; -1 when the key is not an own key of that layer (e.g. a shared row). */
    public static int ownRow(LayoutDef m, String layer, String id) {
        String[] o = owner(m, id);
        if (o == null || !o[0].equals(layer)) return -1;
        return Integer.parseInt(o[1]);
    }

    /** Id of the right (d > 0) or left (d < 0) neighbour of the key in its drawn row, or "". */
    public static String neighbourId(LayoutDef m, String layer, String id, int d) {
        int[] p = locate(m, layer, id);
        if (p == null) return "";
        List<KeyDef> ks = m.effectiveRows(layer).get(p[0]).keys;
        int j = p[1] + (d < 0 ? -1 : 1);
        return (j >= 0 && j < ks.size()) ? ks.get(j).id : "";
    }

    /** Which key to select after a structure change: the same id when it still exists, else the key now at the old place, else the first letter. */
    public static String keepOrNear(LayoutDef m, String layer, String id, int[] loc) {
        if (m == null) return "";
        if (findKey(m, layer, id) != null) return id;
        List<RowDef> rows = m.effectiveRows(layer);
        if (loc != null && rows.size() > 0) {
            int r = Math.max(0, Math.min(loc[0], rows.size() - 1));
            List<KeyDef> ks = rows.get(r).keys;
            if (ks.size() > 0) return ks.get(Math.max(0, Math.min(loc[1], ks.size() - 1))).id;
        }
        return firstCharId(m, layer);
    }

    /** \"Row 2: 9 keys, width 9.5\" for the row badge (\"\" when the key is not drawn). */
    public static String rowSummary(LayoutDef m, String layer, String id) {
        int[] p = locate(m, layer, id);
        if (p == null) return "";
        RowDef r = m.effectiveRows(layer).get(p[0]);
        return "Row " + (p[0] + 1) + ": " + r.keys.size() + (r.keys.size() == 1 ? " key" : " keys") + ", width " + weightText(r.totalWeight()) + ", height " + r.heightPct + "%";
    }

    public static String undoText(boolean can, String label) {
        if (!can) return "Undo";
        return (label == null || label.length() == 0) ? "Undo" : "Undo: " + label;
    }

    public static String redoText(boolean can, String label) {
        if (!can) return "Redo";
        return (label == null || label.length() == 0) ? "Redo" : "Redo: " + label;
    }

    /** Message when LayoutOps refused a change (it returned the same model). */
    public static String refusedText(String what) {
        return "Not possible here: " + (what == null ? "this change" : what) + " (a row needs 1 key, a key at least 0.25 width, max 8 rows / 80 keys).";
    }

    // ---------- v0.17.10 (K7): swipe fields, picker lists, warnings ----------
    public static final String[] SWIPE_NAMES = { "Swipe up", "Swipe down", "Swipe left", "Swipe right" };
    public static final int MAX_WARN_SHOWN = 8, MAX_CHIPS = 40;

    private static String[] cps(String s) {
        List<String> l = new ArrayList<String>();
        for (int i = 0; i < s.length(); ) { int c = s.codePointAt(i); l.add(new String(Character.toChars(c))); i += Character.charCount(c); }
        return l.toArray(new String[0]);
    }

    private static final String[] PICK_TITLES = { "Symbols", "Digits", "Currency (incl. \u09F3)", "Bangla vowels", "Bangla consonants", "Bangla signs (kar)", "Bangla digits", "Actions (swipe / long-press)" };

    public static String[] pickTitles() { return PICK_TITLES.clone(); }

    /** Items of picker group g (0-based, see pickTitles). Unknown group = empty. Group 7 = the @actions of the whitelist (sorted). */
    public static String[] pickItems(int g) {
        switch (g) {
            case 0: return cps("!@#%&*()-+=/\\:;'\",.?_~<>[]{}|^`\u0964");
            case 1: return cps("0123456789");
            case 2: return cps("\u09F3$\u20AC\u00A3\u00A5\u20B9\u00A2\u20A9");
            case 3: return cps("\u0985\u0986\u0987\u0988\u0989\u098A\u098B\u098F\u0990\u0993\u0994");
            case 4: return new String[] { "\u0995","\u0996","\u0997","\u0998","\u0999","\u099A","\u099B","\u099C","\u099D","\u099E","\u099F","\u09A0","\u09A1","\u09A2","\u09A3","\u09A4","\u09A5","\u09A6","\u09A7","\u09A8","\u09AA","\u09AB","\u09AC","\u09AD","\u09AE","\u09AF","\u09B0","\u09B2","\u09B6","\u09B7","\u09B8","\u09B9","\u09DC","\u09DD","\u09DF","\u09CE","\u0982","\u0983","\u0981" };
            case 5: return new String[] { "\u09BE","\u09BF","\u09C0","\u09C1","\u09C2","\u09C3","\u09C7","\u09C8","\u09CB","\u09CC","\u09CD","\u200C","\u200D" };
            case 6: return cps("\u09E6\u09E7\u09E8\u09E9\u09EA\u09EB\u09EC\u09ED\u09EE\u09EF");
            case 7: { String[] a = LayoutText.AT_ACTIONS.toArray(new String[0]); Arrays.sort(a); return a; }
            default: return new String[0];
        }
    }

    /** Readable name of a picker item (ZWJ / ZWNJ and @actions are not visible otherwise). */
    public static String pickName(String item) {
        if (item == null) return "";
        if (item.equals("\u200C")) return "ZWNJ (invisible)";
        if (item.equals("\u200D")) return "ZWJ (invisible)";
        if (item.startsWith("@") && item.length() > 1) return item + " = " + atName(item);
        return item;
    }

    public static String atName(String a) {
        if (a == null) return "";
        switch (a) {
            case "@tab": return "Tab"; case "@undo": return "Undo"; case "@redo": return "Redo"; case "@paste": return "Paste";
            case "@copy": return "Copy"; case "@cut": return "Cut"; case "@selall": return "Select all"; case "@emoji": return "Emoji panel";
            case "@clip": return "Clipboard"; case "@lang": return "Switch language"; case "@caps": return "Caps lock"; case "@sym": return "Symbols";
            case "@left": return "Cursor left"; case "@right": return "Cursor right"; case "@home": return "Line start"; case "@end": return "Line end";
            case "@del_word": return "Delete word";
            default: return a;
        }
    }

    /** Ids of the keys that belong to a warning of [layer] with at least [minSev] (orange outline in the editor). */
    public static java.util.Set<String> warnKeyIds(List<LayoutCheck.Warn> ws, String layer, int minSev) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        if (ws == null) return out;
        for (LayoutCheck.Warn w : ws) if (w != null && w.severity >= minSev && (layer == null || layer.equals(w.layer))) out.addAll(w.keyIds);
        return out;
    }

    /** Warnings of one layer, worst first (stable). */
    public static List<LayoutCheck.Warn> warnsOf(List<LayoutCheck.Warn> ws, String layer) {
        List<LayoutCheck.Warn> out = new ArrayList<LayoutCheck.Warn>();
        if (ws == null) return out;
        for (LayoutCheck.Warn w : ws) if (w != null && (layer == null || layer.equals(w.layer))) out.add(w);
        Collections.sort(out, new java.util.Comparator<LayoutCheck.Warn>() {
            @Override public int compare(LayoutCheck.Warn a, LayoutCheck.Warn b) { return b.severity - a.severity; }
        });
        return out;
    }

    public static String severityName(int sev) { return sev >= LayoutCheck.HIGH ? "High" : (sev == LayoutCheck.MED ? "Medium" : (sev == LayoutCheck.LOW ? "Low" : "Info")); }

    public static String warnLine(LayoutCheck.Warn w) {
        if (w == null) return "";
        return "[" + severityName(w.severity) + "] " + w.text + (w.fixable ? " (can be fixed)" : "");
    }

    /** Letters of the W_COVERAGE warning of [layer] that no key types (chips in the editor, max MAX_CHIPS). */
    public static List<String> coverageItems(List<LayoutCheck.Warn> ws, String layer) {
        List<String> out = new ArrayList<String>();
        if (ws == null) return out;
        for (LayoutCheck.Warn w : ws) {
            if (w == null || !LayoutCheck.W_COVERAGE.equals(w.code) || (layer != null && !layer.equals(w.layer))) continue;
            for (String it : w.items) if (out.size() < MAX_CHIPS && !out.contains(it)) out.add(it);
        }
        return out;
    }

    /** Id of the first empty spot drawn in [layer], or "". */
    public static String firstSpacerId(LayoutDef m, String layer) {
        if (m == null) return "";
        for (RowDef r : m.effectiveRows(layer)) for (KeyDef k : r.keys) if (k.kind == KeyDef.SPACER) return k.id;
        return "";
    }
}
