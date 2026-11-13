package com.mgb.keyboard.layout;

/**
 * v0.17.16 (K11a) pure rules of "hold a key and drag it" in the layout editor (no Android, tested by tools/LayoutDragTest.java).
 * Drop zones of the target key: left quarter = insert BEFORE it, right quarter = insert AFTER it, middle = SWAP the two keys.
 * Rules: both keys must sit in the same row, or in rows of the same layer (a letter can NOT be dragged into the shared number row /
 * bottom row, and not between shared rows). A move that would leave a row without keys, break the layout rules (LayoutOps.valid /
 * fatal) or change nothing is refused. Every function returns the SAME model object when refused (callers test "nm === m"). Never throws.
 */
public final class LayoutDrag {
    private LayoutDrag() { }

    public static final int SWAP = 0, BEFORE = 1, AFTER = 2;
    public static final float EDGE_ZONE = 0.25f;

    /** Drop zone for a touch at x on the key whose edges are [left, right]. */
    public static int zone(float left, float right, float x) {
        float w = right - left;
        if (!(w > 0f)) return SWAP;
        if (x < left + w * EDGE_ZONE) return BEFORE;
        if (x > right - w * EDGE_ZONE) return AFTER;
        return SWAP;
    }

    /** Vertical auto-scroll while dragging: pixels to scroll (negative = up) when y is within [zone] of the visible top / bottom; 0 in the middle. */
    public static int autoScrollDy(float y, float top, float bottom, float zone, float maxStep) {
        if (!(zone > 0f) || !(bottom > top) || !(maxStep > 0f)) return 0;
        if (y < top + zone) {
            float f = (top + zone - y) / zone;
            if (f > 1f) f = 1f;
            return -Math.max(1, Math.round(maxStep * f));
        }
        if (y > bottom - zone) {
            float f = (y - (bottom - zone)) / zone;
            if (f > 1f) f = 1f;
            return Math.max(1, Math.round(maxStep * f));
        }
        return 0;
    }

    private static final class At { LayerDef l; RowDef r; int ki; }

    private static At find(LayoutDef m, String id) {
        if (m == null || id == null) return null;
        for (LayerDef l : m.layers) for (RowDef r : l.rows) {
            if (r.isRef()) continue;
            for (int i = 0; i < r.keys.size(); i++) if (id.equals(r.keys.get(i).id)) { At a = new At(); a.l = l; a.r = r; a.ki = i; return a; }
        }
        return null;
    }

    /** True when drop(m, id, targetId, mode) would change the layout (used while dragging to show the target). */
    public static boolean canDrop(LayoutDef m, String id, String targetId, int mode) {
        try { return drop(m, id, targetId, mode) != m; } catch (Throwable t) { return false; }
    }

    /** Short text for the undo button / note, e.g. "drag q after w". */
    public static String label(int mode) {
        return mode == BEFORE ? "drag key before another" : (mode == AFTER ? "drag key after another" : "drag key swap");
    }

    /** The model after dropping key [id] on key [targetId] with [mode]; the SAME model when refused. */
    public static LayoutDef drop(LayoutDef m, String id, String targetId, int mode) {
        try {
            if (m == null || id == null || targetId == null || id.equals(targetId)) return m;
            if (mode != SWAP && mode != BEFORE && mode != AFTER) return m;
            LayoutDef c = m.copy();
            At s = find(c, id), t = find(c, targetId);
            if (s == null || t == null) return m;
            if (s.r != t.r && s.l != t.l) return m;
            if (mode == SWAP) {
                KeyDef a = s.r.keys.get(s.ki), b = t.r.keys.get(t.ki);
                s.r.keys.set(s.ki, b);
                t.r.keys.set(t.ki, a);
            } else {
                if (s.r.keys.size() < 2) return m;                  // would leave an empty row
                KeyDef k = s.r.keys.remove(s.ki);
                int ti = -1;
                for (int i = 0; i < t.r.keys.size(); i++) if (t.r.keys.get(i).id.equals(targetId)) ti = i;
                if (ti < 0) return m;
                t.r.keys.add(mode == AFTER ? ti + 1 : ti, k);
            }
            LayoutDef n = LayoutOps.normalize(c);
            if (n == null || LayoutOps.valid(n) != null || LayoutOps.fatal(n).size() > 0) return m;
            if (n.equals(m)) return m;
            return n;
        } catch (Throwable e) {
            return m;
        }
    }
}
