package com.mgb.keyboard.typing;

/**
 * v0.50.0 (CM1): numbers and rules of the "mock look" clipboard (clipboard-compact-redesign-mock.html).
 * Pure Java, no Android (tools/ClipMockPlanTest.java). One clip = one line, underline tabs, one-line pinned chip strip,
 * swipe right = pin / unpin, swipe left = delete (Undo as before). Pref clip_mock (default ON, needs the tidy look). OFF = v0.49.0.
 */
public final class ClipMockPlan {
    private ClipMockPlan() { }

    public static final int OPEN_MIN_CHARS = 38;   // longer text (or a line break) gets the small expand arrow
    public static final int SWIPE_DP = 80;         // drag further than this = the action runs
    public static final int MAX_DRAG_DP = 110;     // the row never follows the finger further than this
    public static final int OPEN_MAX_LINES = 6;
    public static final int HOLD_FALLBACK_MS = 450;

    /** Can this clip be opened to show more lines? */
    public static boolean expandable(String t) { return t != null && (t.length() > OPEN_MIN_CHARS || t.indexOf('\n') >= 0); }

    /** One-line text of a closed row: line breaks become a small return sign. */
    public static String oneLine(String t) { return t == null ? "" : t.replace("\r", "").replace("\n", " \u23CE "); }

    /** Swipe result: +1 = pin / unpin (right), -1 = delete (left), 0 = nothing. dx in px. */
    public static int swipe(float dxPx, float density) {
        float th = SWIPE_DP * (density > 0f ? density : 1f);
        if (dxPx >= th) return 1;
        if (dxPx <= -th) return -1;
        return 0;
    }

    /** How far the row follows the finger (px), clamped to +- MAX_DRAG_DP. */
    public static float drag(float dxPx, float density) {
        float m = MAX_DRAG_DP * (density > 0f ? density : 1f);
        return Math.max(-m, Math.min(m, dxPx));
    }

    /** Hold time for select mode: the user's hold time, or 450 ms when the system long-click is chosen (0). */
    public static int holdMs(int userMs) { return userMs > 0 ? userMs : HOLD_FALLBACK_MS; }

    /** The one-line pinned chip strip: only on All, no search, no select mode, and at least one pinned clip. */
    public static boolean stripShown(boolean mock, int chip, boolean searching, boolean selOn, int pinnedN) {
        return mock && chip == ClipFilter.ALL && !searching && !selOn && pinnedN > 0;
    }

    /** Tab text: "All 12". Broken input = the label alone. */
    public static String tabText(String label, int n) { return (label == null ? "" : label) + (n >= 0 ? " " + n : ""); }

    /**
     * Order of a flat list (newest copied first). made[i] = copy time of clip i (0 = unknown). Clips with a known time come first, newest first
     * (equal times keep their order); unknown ones follow in their old order. No time known at all = the old order.
     */
    public static int[] order(long[] made) {
        int n = made == null ? 0 : made.length;
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        if (n < 2) return r;
        for (int i = 1; i < n; i++) {   // stable insertion sort
            int v = r[i];
            int j = i - 1;
            while (j >= 0 && before(made[v], made[r[j]])) { r[j + 1] = r[j]; j--; }
            r[j + 1] = v;
        }
        return r;
    }

    private static boolean before(long a, long b) {
        if (a > 0 && b <= 0) return true;
        if (a <= 0) return false;
        return a > b;
    }
}
