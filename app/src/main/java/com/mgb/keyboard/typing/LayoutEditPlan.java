package com.mgb.keyboard.typing;

import com.mgb.keyboard.layout.KeyDef;
import com.mgb.keyboard.layout.LayoutDef;
import com.mgb.keyboard.layout.LayoutOps;
import com.mgb.keyboard.layout.LayoutText;
import com.mgb.keyboard.layout.RowDef;

import java.util.List;

/**
 * v0.17.8 (K6a) pure rules of the app layout editor (no Android): which inspector fields a key has, width steps, shown labels,
 * key positions for drawing / touch, spoken description. Tested by tools/LayoutEditPlanTest.java.
 */
public final class LayoutEditPlan {
    private LayoutEditPlan() { }

    public static final int F_LABEL = 1, F_OUT = 2, F_SHIFT = 4, F_LP = 8, F_WIDTH = 16;
    public static final float STEP = 0.25f;

    /** Letter layers the editor shows in K6a (symbol pages come with K8). */
    public static final String[] TAB_IDS = { "en", "av", "pb" };

    public static String[] tabTitles() { return new String[] { "English", LangNames.AVRO, LangNames.PROBHAT }; }

    public static String tabId(int i) { return (i >= 0 && i < TAB_IDS.length) ? TAB_IDS[i] : TAB_IDS[0]; }

    /** Inspector fields of a key kind (bit mask). Character key = all; action key / empty spot = width only (K6b adds more). */
    public static int fields(int kind) {
        if (kind == KeyDef.CHAR) return F_LABEL | F_OUT | F_SHIFT | F_LP | F_WIDTH;
        if (kind == KeyDef.ACTION || kind == KeyDef.SPACER) return F_WIDTH;
        return 0;
    }

    public static boolean has(int mask, int field) { return (mask & field) != 0; }

    /** Max length (code points) of the text field: F_LABEL, F_OUT / F_SHIFT, F_LP. 0 = unknown field. */
    public static int limit(int field) {
        if (field == F_LABEL) return LayoutText.MAX_LABEL;
        if (field == F_OUT || field == F_SHIFT) return LayoutText.MAX_OUT;
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
}
