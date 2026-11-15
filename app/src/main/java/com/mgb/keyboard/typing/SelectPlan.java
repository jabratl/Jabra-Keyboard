package com.mgb.keyboard.typing;

/**
 * v0.19.0 (dropdown plan S1) pure rules for the compact dropdown "PillSelect" (UiKit.pillSelect). No Android classes.
 *  - label of the pill = the CURRENT value + " v" (the small down triangle is drawn by Glyphs).
 *  - popup list: at most MAX_ROWS rows visible (then it scrolls), the current row starts with a tick.
 *  - popup position: below the pill when it fits, else above, always inside the screen.
 *  - fallback when the popup cannot be shown: the pill cycles to the next value.
 * All methods are safe for null / empty / odd input and never throw.
 */
public final class SelectPlan {
    private SelectPlan() { }

    public static final int MAX_ROWS = 6;
    /** Sign Glyphs turns into the small drawn down triangle. */
    public static final String TAIL = " \u25BE";
    private static final String TICK = "\u2713 ";

    public static int clamp(int i, int n) {
        if (n <= 0) return 0;
        return i < 0 ? 0 : (i >= n ? n - 1 : i);
    }

    /** Next index for the cycle fallback (wraps). */
    public static int next(int cur, int n) {
        if (n <= 0) return 0;
        return (clamp(cur, n) + 1) % n;
    }

    /** Name of the current value, "" when there are no names. */
    public static String value(String[] names, int cur) {
        try {
            if (names == null || names.length == 0) return "";
            String s = names[clamp(cur, names.length)];
            return s == null ? "" : s;
        } catch (Throwable t) { return ""; }
    }

    /** Text on the pill: "Newest v" (value + tail). */
    public static String label(String[] names, int cur) {
        return value(names, cur) + TAIL;
    }

    /** Spoken text for TalkBack: "Sort: Newest" (title optional). */
    public static String describe(String title, String[] names, int cur) {
        String v = value(names, cur);
        if (title == null || title.trim().isEmpty()) return v;
        return title.trim() + ": " + v;
    }

    /** Text of one popup row: the current one starts with a tick. */
    public static String rowText(String name, boolean current) {
        String n = name == null ? "" : name;
        return current ? TICK + n : n;
    }

    public static int visibleRows(int n) {
        if (n <= 0) return 0;
        return n < MAX_ROWS ? n : MAX_ROWS;
    }

    public static boolean scrolls(int n) { return n > MAX_ROWS; }

    public static int popupHeight(int n, int rowPx) {
        if (rowPx < 0) rowPx = 0;
        return visibleRows(n) * rowPx;
    }

    /** v0.19.6 (S7): the list is never taller than the screen minus both margins (landscape / tiny panels); the caller scrolls when it is cut. */
    public static int capHeight(int popH, int screenH, int marginPx) {
        int m = Math.max(0, marginPx);
        int max = screenH - 2 * m;
        if (max < 1) max = Math.max(1, screenH);
        return popH > max ? max : (popH < 0 ? 0 : popH);
    }

    /** Popup width: at least as wide as the pill and as the content, never wider than the screen minus margins; minW wins only when it fits. */
    public static int popupWidth(int anchorW, int contentW, int screenW, int marginPx, int minW) {
        int max = screenW - 2 * Math.max(0, marginPx);
        if (max < 1) max = Math.max(1, screenW);
        int w = Math.max(Math.max(anchorW, contentW), minW);
        return w > max ? max : (w < 1 ? 1 : w);
    }

    /** Left edge: starts at the pill, pushed back inside the screen. */
    public static int popupX(int anchorX, int popW, int screenW, int marginPx) {
        int m = Math.max(0, marginPx);
        int x = anchorX;
        if (x + popW > screenW - m) x = screenW - m - popW;
        return x < m ? m : x;
    }

    /** true = the list goes BELOW the pill (it fits, or there is more room below than above). */
    public static boolean below(int anchorY, int anchorH, int popH, int screenH, int gapPx) {
        int g = Math.max(0, gapPx);
        int roomBelow = screenH - (anchorY + anchorH) - g;
        int roomAbove = anchorY - g;
        if (popH <= roomBelow) return true;
        if (popH <= roomAbove) return false;
        return roomBelow >= roomAbove;
    }

    /** Top edge of the list: below or above the pill, always inside [margin, screenH - margin - popH]. */
    public static int popupY(int anchorY, int anchorH, int popH, int screenH, int gapPx, int marginPx) {
        int g = Math.max(0, gapPx), m = Math.max(0, marginPx);
        int y = below(anchorY, anchorH, popH, screenH, g) ? anchorY + anchorH + g : anchorY - g - popH;
        int maxY = screenH - m - popH;
        if (y > maxY) y = maxY;
        return y < m ? m : y;
    }
}
