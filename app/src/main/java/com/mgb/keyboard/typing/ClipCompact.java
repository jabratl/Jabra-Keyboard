package com.mgb.keyboard.typing;

/**
 * v0.33.0 (CC1): COMPACT clipboard header (step 1 of PLAN_CLIP_COMPACT.txt).
 * Pure Java, no Android (tools/ClipCompactTest.java). Only numbers: the top row (ABC | title | Search | Add | menu / Done)
 * gets a lower height, narrower ABC / Done and a wider Search, and the panel padding is smaller.
 * compact OFF = EXACTLY the old numbers (40 dp row, weights 1.0 / 1.6 / 1.3, padding 4 dp), so the old look is the fallback.
 * The result still goes through ClipBtn.heightDp (pref clip_btn_pct) in the panel, so the user size % keeps working.
 */
public final class ClipCompact {
    private ClipCompact() { }

    public static final int ROW_DP = 34;      // compact top row; touch floor of the kit is 28 dp (ClipBtn.MIN_DP)
    public static final int PAD_V_OLD = 4, PAD_V_NEW = 2;

    public static final int ABC = 0, SEARCH = 1, DONE = 2;

    /** Height (dp) of the top row. OFF or broken input = the old height. Never above the old height, never below ClipBtn.MIN_DP. */
    public static int rowDp(int oldDp, boolean compact) {
        if (!compact || oldDp <= 0) return oldDp;
        int h = Math.min(oldDp, ROW_DP);
        return h < ClipBtn.MIN_DP ? Math.min(oldDp, ClipBtn.MIN_DP) : h;
    }

    /** Weight of a top row button. which = ABC / SEARCH / DONE (anything else = 1). OFF = old weights. */
    public static float weight(int which, boolean compact, boolean slim) {
        if (!compact) {
            if (which == ABC) return 1.0f;
            if (which == SEARCH) return slim ? 0.9f : 1.6f;
            if (which == DONE) return slim ? 0.8f : 1.3f;
            return 1.0f;
        }
        if (which == ABC) return 0.7f;
        if (which == SEARCH) return slim ? 0.8f : 2.4f;
        if (which == DONE) return slim ? 0.7f : 1.0f;
        return 1.0f;
    }

    /** Top / bottom padding (dp) of the whole panel. */
    public static int padV(boolean compact) { return compact ? PAD_V_NEW : PAD_V_OLD; }

    /** Height the compact header gives back to the list, in dp (old row - new row, after the padding change). Used by the checklist text. */
    public static int savedDp(int oldDp) { return (oldDp - rowDp(oldDp, true)) + 2 * (PAD_V_OLD - PAD_V_NEW); }
}
