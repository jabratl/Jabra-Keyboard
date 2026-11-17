package com.mgb.keyboard.typing;

/**
 * v0.37.0: pure numbers of the FLOATING select bar (a rounded pill that floats over the bottom of the list, only in select mode).
 * No Android here (tools/ClipFloatTest.java). The pill takes NO layout height: it is pulled over the list with a negative top margin,
 * and the list gets the same height as bottom padding so the last clip can always be scrolled above the pill.
 */
public final class ClipFloat {
    private ClipFloat() { }

    public static final int PILL_MIN_DP = 36;   // finger target of the pill
    public static final int GAP_BOTTOM_DP = 8;  // air under the pill (the shadow needs it)
    public static final int SIDE_DP = 10;       // air left / right of the pill
    public static final int ICON_DP = 16;
    public static final int TEXT_SP = 12;

    /** Pill height: the slim bar height, never below PILL_MIN_DP. */
    public static int pillDp(int barDp) { return Math.max(PILL_MIN_DP, barDp); }

    /** Height the pill covers over the list (pill + gap). The row is pulled up by this much, so the net layout height is 0. */
    public static int coverDp(int barDp) { return pillDp(barDp) + GAP_BOTTOM_DP; }

    /** Bottom padding of the list: what the pill covers + 4 dp air. */
    public static int listPadDp(int barDp) { return coverDp(barDp) + 4; }

    /** The pill shows only in select mode, new look, pref ON, and when no old tray is open and no search keyboard is shown. */
    public static boolean wants(boolean selOn, boolean prefOn, boolean searching, boolean trayOpen) {
        return selOn && prefOn && !searching && !trayOpen;
    }

    /** The slide-in plays only when the pill was NOT there in the previous build (every tap rebuilds the panel; it must not flicker). */
    public static boolean animate(boolean wasShown) { return !wasShown; }
}
