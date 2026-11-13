package com.mgb.keyboard.typing;

/**
 * v0.17.32 (UI8): size of the buttons in the Clipboard panel (ABC, Search, Add, Manage, chips, Undo / Sort / View / Clear, Pin / Delete bar).
 * Pure Java, no Android (tools/ClipBtnTest.java). One percent pref: clip_btn_pct, 70..140, 100 = EXACTLY the old size.
 * Unset / broken value = 100. A button is never lower than MIN_DP (touch size). Smaller buttons free height: with
 * "Clipboard follows keyboard height" ON the clip list grows by the freed height by itself (ClipFit), bigger buttons shrink it.
 */
public final class ClipBtn {
    private ClipBtn() { }

    public static final int MIN = 70, MAX = 140, STEP = 10, DEF = 100;
    public static final int MIN_DP = 28;

    public static boolean valid(int pct) { return pct >= MIN && pct <= MAX; }

    /** Valid percent stays; anything else (unset -1, 0, 999 ...) = 100 = old size. */
    public static int sanitize(int pct) { return valid(pct) ? pct : DEF; }

    /** Height in dp for an old dp height. 100 = unchanged. Scaled heights never go below MIN_DP (unless the old height was already lower). */
    public static int heightDp(int oldDp, int pct) {
        int p = sanitize(pct);
        if (p == DEF || oldDp <= 0) return oldDp;
        int h = Math.round(oldDp * p / 100f);
        int floor = Math.min(oldDp, MIN_DP);
        return h < floor ? floor : h;
    }

    /** Text size (same unit as the input) scaled by the percent. 100 = unchanged. */
    public static float textSize(float old, int pct) {
        int p = sanitize(pct);
        return p == DEF ? old : old * p / 100f;
    }
}
