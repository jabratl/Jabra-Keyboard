package com.mgb.keyboard.typing;

import java.util.IdentityHashMap;

/**
 * v0.9.9 (plan V4) key bubble without window calls (pure Java, no Android, tested by tools/BubblePlanTest.java).
 * Decides where the key bubble is drawn: OVERLAY = a view inside the keyboard container (no PopupWindow.update),
 * WINDOW = the v0.9.6 PopupWindow path. A bubble fits as overlay only when it stays fully INSIDE the container
 * (key top - bubble height - gap >= 0): rows 2-4 normally, the top letter row / number row only when the toolbar
 * or the Probhat extra row gives room above them. Everything else keeps the window (no room above).
 * Also holds the key-rect cache (container-relative rects, cleared on every layout change) and the container-relative x / y.
 */
public final class BubblePlan {
    private BubblePlan() { }

    public static final int WINDOW = 0;
    public static final int OVERLAY = 1;

    /** OVERLAY when the setting is ON, the host container is known and the bubble fits inside it; else WINDOW. */
    public static int mode(boolean fastOn, boolean hostReady, int keyTop, int bubbleH, int gapPx) {
        if (!fastOn || !hostReady) return WINDOW;
        if (bubbleH <= 0 || keyTop < 0) return WINDOW;
        return (keyTop - bubbleH - gapPx) >= 0 ? OVERLAY : WINDOW;
    }

    /** Left edge inside the container: centred over the key, kept inside 0..hostW. */
    public static int x(int keyLeft, int keyW, int w, int hostW) { return TipPlace.x(keyLeft, keyW, w, hostW); }

    /** Top edge inside the container (not clamped: mode() only says OVERLAY when this is >= 0). */
    public static int y(int keyTop, int h, int gapPx) { return keyTop - h - gapPx; }

    /** Sum of the offsets of a view and its parents up to (not including) the container = key position inside the container. */
    public static int sum(int[] parts) {
        if (parts == null) return 0;
        int s = 0;
        for (int p : parts) s += p;
        return s;
    }

    /** A cached rect {left, top, w, h} is still usable only when the key has the same size as when it was cached. */
    public static boolean rectValid(int[] r, int w, int h) {
        return r != null && r.length == 4 && r[2] == w && r[3] == h && w > 0 && h > 0;
    }

    /** Container-relative key rects, one per key view (identity). clear() on any layout change / keyboard rebuild. */
    public static final class RectCache<K> {
        private final IdentityHashMap<K, int[]> m = new IdentityHashMap<K, int[]>();
        public int[] get(K k) { return k == null ? null : m.get(k); }
        public void put(K k, int left, int top, int w, int h) { if (k != null) m.put(k, new int[] { left, top, w, h }); }
        public void clear() { m.clear(); }
        public int size() { return m.size(); }
    }
}
