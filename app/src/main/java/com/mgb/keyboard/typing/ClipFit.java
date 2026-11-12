package com.mgb.keyboard.typing;

/**
 * v0.15.1 (N1) pure helper: the clipboard panel keeps the height of the letters keyboard (pref clip_fit, default ON).
 * The panel is built as before, measured, and the clip list grows by the missing height. Never shrinks, never throws.
 * All values are pixels except MAX_EXTRA_DP.
 */
public final class ClipFit {
    private ClipFit() {}

    /** The list never grows by more than this (dp) on top of its old fixed height (176 dp / 88 dp while searching). */
    public static final int MAX_EXTRA_DP = 260;

    /** Extra pixels to add to the list: target (letters keyboard height) minus measured panel height, clamped to 0..maxExtraPx. Unknown / bad values = 0 (old behaviour). */
    public static int extraPx(int targetPx, int measuredPx, int maxExtraPx) {
        if (targetPx <= 0 || measuredPx <= 0 || maxExtraPx <= 0) return 0;
        int d = targetPx - measuredPx;
        if (d <= 0) return 0;
        return d > maxExtraPx ? maxExtraPx : d;
    }

    /** Pref value to on/off: only an explicit 0 / false switches it off (default ON). */
    public static boolean on(boolean prefValue) { return prefValue; }
}
