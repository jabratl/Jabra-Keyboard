package com.mgb.keyboard.typing;

/** v0.1.1 level tables for the 2 "cycle" settings (pure Java). Level 0 / 2 are the defaults = exactly the old behaviour. */
public final class TypingLevels {
    private TypingLevels() { }

    public static final String[] LP_NAMES = { "System", "Fast", "Medium", "Slow", "Very slow" };
    private static final int[] LP_MS = { 0, 250, 350, 650, 900 };   // 0 = the phone's own long-press time (old behaviour)
    public static final int LP_DEFAULT = 0;

    public static final String[] CS_NAMES = { "Slowest", "Slow", "Normal", "Fast", "Fastest" };
    private static final float[] CS_DP = { 28f, 22f, 16f, 11f, 7f };   // finger distance (dp) for one cursor step; 16 = old value
    public static final int CS_DEFAULT = 2;

    private static int clamp(int lv, int n) { return lv < 0 ? 0 : (lv >= n ? n - 1 : lv); }

    public static int longPressMs(int level) { return LP_MS[clamp(level, LP_MS.length)]; }

    public static float stepDp(int level) { return CS_DP[clamp(level, CS_DP.length)]; }
}
