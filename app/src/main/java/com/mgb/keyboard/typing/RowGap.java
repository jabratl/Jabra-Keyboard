package com.mgb.keyboard.typing;

/**
 * v0.10.25: home-row side gap (Ridmik-like). Pure Java, no Android.
 * English / Avro: the top row (q..p) has 10 keys, the home row (a..l) has 9. Without a gap the 9 keys are stretched
 * over the whole width (wider than the keys above). With the gap the home row keys get the SAME width as the row above
 * and half a key of free space stays on each side. A finger-down in that free space is sent to the nearest key by GapRow.
 * Probhat: the home row already has 10 keys (a..l + one extra key) = full width, so no gap (same as before).
 */
public final class RowGap {
    private RowGap() {}

    /** Number of key columns of the top letter row (q..p) and of the number row. */
    public static final int FULL = 10;

    /** Weight of ONE side spacer. 0 = no spacer. [on] = pref row_gap, [rowKeys] = visible keys in the home row. */
    public static float side(boolean on, int rowKeys) {
        if (!on) return 0f;
        if (rowKeys <= 0 || rowKeys >= FULL) return 0f;
        float w = (FULL - rowKeys) / 2f;
        return w > 1.5f ? 1.5f : w;
    }

    /** Visible keys in the home row: 9 letters, +1 when the Probhat extra key is shown. */
    public static int homeKeys(boolean probhat) { return probhat ? 10 : 9; }

    /** Percent of the screen width that the free space takes on ONE side (for the preview note). */
    public static int sidePercent(boolean on, int rowKeys) {
        float s = side(on, rowKeys);
        float total = rowKeys + 2f * s;
        return total <= 0f ? 0 : Math.round(s * 100f / total);
    }
}
