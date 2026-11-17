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

    // ---- v0.34.0 (CC2): filter row + Undo in ONE row (step 2 of PLAN_CLIP_COMPACT.txt) ----
    public static final int FILTER_ROW = 1, INFO_ROW = 2, UNDO_IN_FILTER = 4;

    /**
     * Which of the two header rows under the top row exist, as bit flags.
     * compact2 OFF = EXACTLY the v0.33.0 rows: the info line exists unless it is select mode with nothing to say (old "idleSel"), never while searching.
     * compact2 ON  = Undo moves to the end of the filter row (only when that row is shown, not while confirming, not while searching);
     *   the info line then exists only for a message, the "Delete all unpinned?" confirm, or an Undo that has no filter row to live in.
     * The clip count is not an info line any more in compact2 (the title of the top row already shows it).
     */
    public static int rowPlan(boolean compact2, boolean filterShown, boolean selOn, boolean undo, boolean hasMsg, boolean confirm, boolean searching) {
        int r = filterShown ? FILTER_ROW : 0;
        if (searching) return r;
        if (!compact2) {
            boolean idleSel = selOn && !hasMsg && !confirm && !undo;
            return idleSel ? r : (r | INFO_ROW);
        }
        boolean undoIn = undo && filterShown && !confirm;
        if (undoIn) r |= UNDO_IN_FILTER;
        if (hasMsg || confirm || (undo && !undoIn)) r |= INFO_ROW;
        return r;
    }

    public static boolean hasInfo(int plan) { return (plan & INFO_ROW) != 0; }
    public static boolean hasFilter(int plan) { return (plan & FILTER_ROW) != 0; }
    public static boolean undoInFilter(int plan) { return (plan & UNDO_IN_FILTER) != 0; }

    // ---- v0.35.0 (CC3-A): slim bottom select bar ----
    public static final int BAR_SLIM_DP = 34, BAR_FLOOR_DP = 28;

    /** Select-bar button height in dp (before the clip_btn_pct scaling). slim OFF = old. slim ON = 34, never above old, never below 28 (unless old was already lower). */
    public static int barDp(int oldDp, boolean slim) {
        if (!slim || oldDp <= 0) return oldDp;
        int h = Math.min(oldDp, BAR_SLIM_DP);
        int floor = Math.min(oldDp, BAR_FLOOR_DP);
        return h < floor ? floor : h;
    }

    /** Text of the Done button. slim OFF or nothing selected = "Done". Else "3 \u00B7 Done", "99+ \u00B7 Done" above 99. */
    public static String doneLabel(int n, boolean slim) {
        if (!slim || n <= 0) return "Done";
        return (n > 99 ? "99+" : String.valueOf(n)) + " \u00B7 Done";
    }

    /** The old "N selected" text inside the bar: only the old bar, and not in the delete confirm. */
    public static boolean showCountInBar(boolean slim, boolean confirm) { return !slim && !confirm; }

    /** Rows that exist under the top row (0, 1 or 2). */
    public static int rowCount(int plan) { return (hasInfo(plan) ? 1 : 0) + (hasFilter(plan) ? 1 : 0); }
}
